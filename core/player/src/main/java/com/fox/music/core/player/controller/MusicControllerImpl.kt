package com.fox.music.core.player.controller

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fox.music.core.common.player.PlaybackStateStore
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackSnapshot
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.player.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MusicControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateStore: PlaybackStateStore,
    private val recordPlayUseCase: com.fox.music.core.domain.usecase.RecordPlayUseCase,
) : MusicController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // 上一首歌的播放信息，用于在切歌时记录播放进度
    private var lastTrackId: Long? = null
    private var lastTrackDurationMs: Long = 0L
    private var lastTrackPositionMs: Long = 0L

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var currentPlaylist: List<Music> = emptyList()
    private var playingKey: String? = null
    private var currentRepeatMode: RepeatMode = RepeatMode.ALL

    private var pendingAutoPlay = false
    private var isRestoring = false
    private var hasRestored = false
    /** 用户已主动点播，跳过冷启动恢复以免覆盖当前队列 */
    private var userPlaylistActive = false

    /** 用户点播的曲目 ID，UI 以此为准，不受 ExoPlayer 索引抖动影响 */
    private var userAnchorMusicId: Long? = null

    /** ExoPlayer 在 setMediaItems 后索引可能短暂滞后，此字段用于保持 UI 与目标曲目一致 */
    private var expectedMediaItemIndex: Int? = null
    private var lastUserSetPlaylistTimeMs = 0L

    private var lastPersistedPositionMs = 0L
    private var lastPersistTimeMs = 0L

    /** 每次 apply/set 递增，用于忽略过期的 Player 回调 */
    private var activePlaylistGeneration = 0
    /** apply 后短暂窗口内忽略 ENDED 触发的重入逻辑 */
    private var applyingPlaylistUntilMs = 0L
    /** 冷启动 UI 恢复后的待续播进度，在 Exo 未对齐前不被 timeline 轮询覆盖 */
    private var pendingRestorePositionMs: Long? = null

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        controllerFuture =
            MediaController.Builder(context, sessionToken)
                .buildAsync().also { future ->
                    future.addListener(
                        {
                            controller = future.get()
                            setupPlayerListener()
                            startPositionUpdates()
                            scope.launch { onControllerConnected() }
                        },
                        MoreExecutors.directExecutor()
                    )
                }
    }

    private suspend fun onControllerConnected() {
        if (hasRestored) return

        val savedMode = playbackStateStore.loadRepeatMode()
        currentRepeatMode = savedMode
        applyRepeatModeToPlayer(savedMode)

        if (shouldSkipRestoreForUserPlaylist()) {
            applyUserPlaylistOnConnect()
            hasRestored = true
            return
        }

        isRestoring = true
        try {
            val snapshot = playbackStateStore.loadSnapshot()
            if (shouldSkipRestoreForUserPlaylist()) {
                applyUserPlaylistOnConnect()
                return
            }
            if (snapshot != null && snapshot.isValid) {
                restoreFromSnapshot(snapshot)
            } else {
                updatePlayerState()
            }
        } finally {
            isRestoring = false
            hasRestored = true
        }
    }

    private fun shouldSkipRestoreForUserPlaylist(): Boolean {
        return userPlaylistActive && currentPlaylist.isNotEmpty()
    }

    private fun applyUserPlaylistOnConnect() {
        val index = (expectedMediaItemIndex ?: _playerState.value.currentIndex)
            .coerceIn(0, currentPlaylist.lastIndex)
        applyPlaylistToPlayer(
            startIndex = index,
            startPositionMs = _playerState.value.position,
            autoPlay = pendingAutoPlay,
        )
    }

    private fun restoreFromSnapshot(snapshot: PlaybackSnapshot) {
        if (userPlaylistActive) return
        playingKey = snapshot.playingKey
        currentPlaylist = snapshot.playlist
        currentRepeatMode = snapshot.repeatMode
        pendingAutoPlay = false

        val safeIndex = snapshot.currentIndex.coerceIn(0, snapshot.playlist.lastIndex)
        userAnchorMusicId = snapshot.playlist.getOrNull(safeIndex)?.id
        expectedMediaItemIndex = safeIndex
        userPlaylistActive = true
        applyRepeatModeToPlayer(snapshot.repeatMode)
        // 冷启动仅恢复 UI/内存状态，不加载 ExoPlayer，避免与用户点播争用队列
        pendingRestorePositionMs = snapshot.positionMs.coerceAtLeast(0L)
        publishOptimisticPlayerState(
            musics = snapshot.playlist,
            index = safeIndex,
            positionMs = pendingRestorePositionMs!!,
        )
        _currentPosition.value = pendingRestorePositionMs!!
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState()
                if (!isPlaying && !isRestoring) {
                    persistPlaybackState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = controller ?: return
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (pendingAutoPlay) {
                            player.play()
                            pendingAutoPlay = false
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (isApplyingPlaylist()) {
                            updatePlayerState()
                            return
                        }
                        if (player.mediaItemCount == 0 && currentPlaylist.isNotEmpty()) {
                            updatePlayerState()
                            return
                        }
                        if (userPlaylistActive && player.mediaItemCount == 1) {
                            if (!pendingAutoPlay && !player.playWhenReady) {
                                updatePlayerState()
                                return
                            }
                            handleUserPlaylistTrackEnded(player)
                            updatePlayerState()
                            return
                        }
                        if (!pendingAutoPlay && !player.playWhenReady) {
                            updatePlayerState()
                            return
                        }
                        val targetIndex = resolveUiIndex(player.currentMediaItemIndex)
                        player.seekToDefaultPosition(targetIndex)
                        player.prepare()
                        if (pendingAutoPlay || player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
                updatePlayerState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {}

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 记录上一首的播放进度（fire-and-forget）
                val prevId = lastTrackId
                if (prevId != null && prevId > 0) {
                    val durationSec = (lastTrackDurationMs / 1000).toInt().takeIf { it > 0 }
                    val progress = if (lastTrackDurationMs > 0) {
                        ((lastTrackPositionMs * 100) / lastTrackDurationMs).toInt().coerceIn(0, 100)
                    } else null
                    scope.launch {
                        recordPlayUseCase(prevId, durationSec, progress)
                    }
                }

                val player = controller
                if (userPlaylistActive && player != null && !isApplyingPlaylist()) {
                    val anchorId = userAnchorMusicId
                    mediaItem?.mediaId?.toLongOrNull()?.takeIf { id ->
                        anchorId == null || id == anchorId
                    }?.let { userAnchorMusicId = it }
                } else {
                    mediaItem?.mediaId?.toLongOrNull()?.let { userAnchorMusicId = it }
                }

                // 更新当前曲目追踪信息
                val newId = mediaItem?.mediaId?.toLongOrNull()
                lastTrackId = newId
                lastTrackDurationMs = player?.duration?.takeIf { it > 0 } ?: 0L
                lastTrackPositionMs = 0L

                updatePlayerState()
                if (!isRestoring) {
                    persistPlaybackState()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePlayerState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updatePlayerState()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                updatePlayerState()
            }
        })
    }

    private fun startPositionUpdates() {
        scope.launch {
            while (isActive) {
                controller?.let { player ->
                    val uiIndex = resolveUiIndex(player.currentMediaItemIndex)
                    val timelineReady = canUseExoTimeline(player, uiIndex)
                    if (timelineReady) {
                        _currentPosition.value = player.currentPosition
                        lastTrackPositionMs = player.currentPosition
                        if (player.duration > 0) {
                            lastTrackDurationMs = player.duration
                        }
                    }
                    updatePlayerState()
                    val positionForPersist = if (timelineReady) {
                        player.currentPosition
                    } else {
                        _playerState.value.position
                    }
                    maybePersistPosition(positionForPersist)
                }
                delay(500L)
            }
        }
    }

    private fun maybePersistPosition(positionMs: Long) {
        if (isRestoring || currentPlaylist.isEmpty()) return
        val now = System.currentTimeMillis()
        val positionDelta = kotlin.math.abs(positionMs - lastPersistedPositionMs)
        if (now - lastPersistTimeMs >= POSITION_PERSIST_INTERVAL_MS ||
            positionDelta >= POSITION_PERSIST_DELTA_MS
        ) {
            persistPlaybackState(positionMs)
        }
    }

    private fun buildSnapshot(positionOverrideMs: Long? = null): PlaybackSnapshot? {
        if (isRestoring) return null
        val playlist = currentPlaylist
        if (playlist.isEmpty()) return null
        val player = controller
        val index = userAnchorMusicId?.let { id ->
            playlist.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: player?.currentMediaItemIndex ?: _playerState.value.currentIndex
        val position = positionOverrideMs ?: player?.currentPosition ?: _playerState.value.position
        return PlaybackSnapshot(
            playingKey = playingKey,
            playlist = playlist,
            currentIndex = index.coerceIn(0, playlist.lastIndex),
            positionMs = position.coerceAtLeast(0L),
            repeatMode = currentRepeatMode,
        )
    }

    private fun isApplyingPlaylist(): Boolean =
        System.currentTimeMillis() < applyingPlaylistUntilMs

    private fun persistPlaybackState(positionOverrideMs: Long? = null) {
        if (isApplyingPlaylist()) return
        val snapshot = buildSnapshot(positionOverrideMs) ?: return
        lastPersistedPositionMs = snapshot.positionMs
        lastPersistTimeMs = System.currentTimeMillis()
        scope.launch {
            playbackStateStore.saveSnapshot(snapshotForPersist(snapshot))
        }
    }

    /** 持久化时仅保留当前曲目的歌词，其余条目去掉大字段，避免 OOM */
    private fun snapshotForPersist(snapshot: PlaybackSnapshot): PlaybackSnapshot {
        if (snapshot.playlist.isEmpty()) return snapshot
        val currentIndex = snapshot.currentIndex.coerceIn(0, snapshot.playlist.lastIndex)
        return snapshot.copy(
            playlist = snapshot.playlist,
        )
    }

    override fun flushPlaybackState() {
        val snapshot = buildSnapshot() ?: return
        lastPersistedPositionMs = snapshot.positionMs
        lastPersistTimeMs = System.currentTimeMillis()
        runBlocking {
            playbackStateStore.saveSnapshot(snapshotForPersist(snapshot))
        }
    }

    private fun resolveUiIndex(exoIndex: Int): Int {
        if (currentPlaylist.isEmpty()) return 0
        userAnchorMusicId?.let { anchorId ->
            val byId = currentPlaylist.indexOfFirst { it.id == anchorId }
            if (byId >= 0) return byId
        }
        expectedMediaItemIndex?.let { return it.coerceIn(0, currentPlaylist.lastIndex) }
        return exoIndex.coerceIn(0, currentPlaylist.lastIndex)
    }

    private fun canUseExoTimeline(player: Player, uiIndex: Int): Boolean {
        if (player.mediaItemCount == 0) return false
        if (userPlaylistActive) {
            if (player.mediaItemCount != 1) return false
            val playingId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return false
            val expectedId = userAnchorMusicId ?: currentPlaylist.getOrNull(uiIndex)?.id
            return playingId == expectedId
        }
        return player.currentMediaItemIndex == uiIndex && isExoQueueAligned(player)
    }

    private fun resolveDurationMs(
        player: Player,
        uiIndex: Int,
        currentMusic: Music?,
        canUseExoTimeline: Boolean,
    ): Long {
        val rawDuration = if (canUseExoTimeline) player.duration else C.TIME_UNSET
        val resolved = when {
            rawDuration >= 0 -> rawDuration
            currentMusic != null && currentMusic.duration in 1..999 -> currentMusic.duration * 1000L
            currentMusic != null && currentMusic.duration >= 1000L -> currentMusic.duration
            else -> 0L
        }
        if (resolved <= 0L && canUseExoTimeline && player.duration >= 0) {
            return player.duration
        }
        return resolved
    }

    private fun updatePlayerState() {
        val player = controller ?: return
        if (isRestoring && userPlaylistActive) {
            return
        }
        val exoIndex = player.currentMediaItemIndex
        val currentIndex = resolveUiIndex(exoIndex)
        val currentMusic = currentPlaylist.getOrNull(currentIndex)
        val resolvedRepeatMode = if (userPlaylistActive) {
            currentRepeatMode
        } else {
            player.toRepeatMode().also { currentRepeatMode = it }
        }
        val canUseExoTimeline = canUseExoTimeline(player, currentIndex)
        val durationMs = resolveDurationMs(player, currentIndex, currentMusic, canUseExoTimeline)
            .takeIf { it > 0L }
            ?: _playerState.value.duration.takeIf {
                currentMusic?.id == _playerState.value.currentMusic?.id && it > 0L
            } ?: 0L
        _playerState.value = PlayerState(
            currentMusic = currentMusic,
            playlist = currentPlaylist,
            currentIndex = currentIndex,
            isPlaying = player.isPlaying,
            isLoading = player.playbackState == Player.STATE_BUFFERING,
            position = if (canUseExoTimeline) player.currentPosition else _playerState.value.position,
            duration = durationMs,
            repeatMode = resolvedRepeatMode,
            shuffleMode = if (userPlaylistActive) {
                currentRepeatMode == RepeatMode.RANDOM
            } else {
                player.shuffleModeEnabled
            },
            playbackSpeed = player.playbackParameters.speed,
            isFavorite = currentMusic?.isFavorite ?: false,
        )
    }

    override fun updateCurrentMusicFavorite(isFavorite: Boolean) {
        val anchorId = userAnchorMusicId ?: _playerState.value.currentMusic?.id ?: return
        currentPlaylist = currentPlaylist.map { music ->
            if (music.id == anchorId) music.copy(isFavorite = isFavorite) else music
        }
        val state = _playerState.value
        val updatedMusic = state.currentMusic?.takeIf { it.id == anchorId }?.copy(isFavorite = isFavorite)
        _playerState.value = state.copy(
            currentMusic = updatedMusic ?: state.currentMusic,
            playlist = currentPlaylist,
            isFavorite = if (state.currentMusic?.id == anchorId) isFavorite else state.isFavorite,
        )
    }

    private fun syncExoToAnchor(player: Player) {
        if (!userPlaylistActive || userAnchorMusicId == null) return
        val uiIndex = resolveUiIndex(player.currentMediaItemIndex)
        if (player.currentMediaItemIndex == uiIndex) return
        player.seekToDefaultPosition(uiIndex)
        if (player.playbackState == Player.STATE_ENDED ||
            player.playbackState == Player.STATE_IDLE
        ) {
            player.prepare()
        }
    }

    private fun ensurePlayerReadyToPlay(player: Player) {
        when (player.playbackState) {
            Player.STATE_ENDED, Player.STATE_IDLE -> {
                val targetIndex = resolveUiIndex(player.currentMediaItemIndex)
                player.seekToDefaultPosition(targetIndex)
                player.prepare()
            }
        }
    }

    override fun play() {
        controller?.let { player ->
            val uiIndex = resolveUiIndex(player.currentMediaItemIndex)
            if (currentPlaylist.isNotEmpty() &&
                (player.mediaItemCount == 0 || !isCurrentTrackAligned(player))
            ) {
                if (!isApplyingPlaylist()) {
                    val hadPendingRestore = pendingRestorePositionMs != null
                    val resumePositionMs = pendingRestorePositionMs
                        ?: _playerState.value.position.coerceAtLeast(0L)
                    pendingRestorePositionMs = null
                    userPlaylistActive = true
                    applyTrackToPlayer(uiIndex, resumePositionMs, autoPlay = true)
                }
                return@let
            }
            syncExoToAnchor(player)
            ensurePlayerReadyToPlay(player)
            player.playWhenReady = true
            player.play()
        }
    }

    override fun pause() {
        controller?.pause()
        persistPlaybackState()
    }

    override fun togglePlay() {
        controller?.let {
            if (it.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    override fun stop() {
        val player = controller ?: return
        player.stop()
        persistPlaybackState()
    }

    private fun getPlaylistMediaIdAt(player: Player, index: Int): Long? {
        if (index !in 0 until player.mediaItemCount) return null
        return player.getMediaItemAt(index).mediaId.toLongOrNull()
    }

    private fun isExoQueueAligned(player: Player): Boolean {
        if (player.mediaItemCount != currentPlaylist.size) return false
        return currentPlaylist.indices.all { index ->
            getPlaylistMediaIdAt(player, index) == currentPlaylist[index].id
        }
    }

    /** 用户点播模式：ExoPlayer 仅持当前单曲，与内存歌单锚点比对 */
    private fun isCurrentTrackAligned(player: Player): Boolean {
        if (!userPlaylistActive) return isExoQueueAligned(player)
        if (player.mediaItemCount != 1) return false
        val anchorId = userAnchorMusicId ?: return false
        return player.currentMediaItem?.mediaId?.toLongOrNull() == anchorId
    }

    private fun navigateToPlaylistIndex(targetIndex: Int, autoPlay: Boolean) {
        if (currentPlaylist.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(0, currentPlaylist.lastIndex)
        userAnchorMusicId = currentPlaylist[safeIndex].id
        expectedMediaItemIndex = safeIndex
        val player = controller
        if (player == null) {
            publishOptimisticPlayerState(currentPlaylist, safeIndex)
            return
        }
        if (userPlaylistActive) {
            applyTrackToPlayer(safeIndex, 0L, autoPlay)
            return
        }
        val queueAligned = isExoQueueAligned(player)
        if (queueAligned) {
            ensurePlayerReadyToPlay(player)
            player.seekToDefaultPosition(safeIndex)
            player.playWhenReady = autoPlay
            if (autoPlay) player.play()
            updatePlayerState()
        } else {
            applyPlaylistToPlayer(safeIndex, 0L, autoPlay)
        }
    }

    override fun next() {
        val player = controller ?: return
        if (currentPlaylist.isEmpty()) return
        if (userPlaylistActive) {
            val currentIndex = resolveUiIndex(player.currentMediaItemIndex)
            val nextIndex = when (currentRepeatMode) {
                RepeatMode.SEQUENTIAL -> {
                    if (currentIndex >= currentPlaylist.lastIndex) return
                    currentIndex + 1
                }
                RepeatMode.ONE -> currentIndex
                RepeatMode.ALL -> (currentIndex + 1) % currentPlaylist.size
                RepeatMode.RANDOM -> pickRandomNextIndex(currentIndex)
            }
            navigateToPlaylistIndex(nextIndex, autoPlay = true)
            return
        }
        if (player.shuffleModeEnabled && currentRepeatMode == RepeatMode.RANDOM) {
            if (player.mediaItemCount == 0) {
                navigateToPlaylistIndex(0, autoPlay = true)
                return
            }
            ensurePlayerReadyToPlay(player)
            if (!player.hasNextMediaItem()) return
            player.seekToNextMediaItem()
            player.playWhenReady = true
            player.play()
            currentPlaylist.getOrNull(player.currentMediaItemIndex)?.id?.let { userAnchorMusicId = it }
            updatePlayerState()
            return
        }
        val currentIndex = resolveUiIndex(player.currentMediaItemIndex)
        val nextIndex = when (currentRepeatMode) {
            RepeatMode.SEQUENTIAL -> {
                if (currentIndex >= currentPlaylist.lastIndex) return
                currentIndex + 1
            }
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL, RepeatMode.RANDOM -> (currentIndex + 1) % currentPlaylist.size
        }
        navigateToPlaylistIndex(nextIndex, autoPlay = true)
    }

    override fun previous() {
        val player = controller ?: return
        if (currentPlaylist.isEmpty()) return
        if (userPlaylistActive) {
            val currentIndex = resolveUiIndex(player.currentMediaItemIndex)
            val positionMs = _playerState.value.position.coerceAtLeast(0L)
            if (positionMs > PREVIOUS_RESTART_THRESHOLD_MS) {
                seekTo(0L)
                play()
                return
            }
            val prevIndex = when (currentRepeatMode) {
                RepeatMode.SEQUENTIAL -> {
                    if (currentIndex <= 0) return
                    currentIndex - 1
                }
                RepeatMode.ONE -> currentIndex
                RepeatMode.ALL, RepeatMode.RANDOM -> {
                    if (currentIndex > 0) currentIndex - 1 else currentPlaylist.lastIndex
                }
            }
            navigateToPlaylistIndex(prevIndex, autoPlay = true)
            return
        }
        if (player.shuffleModeEnabled && currentRepeatMode == RepeatMode.RANDOM) {
            ensurePlayerReadyToPlay(player)
            if (!player.hasPreviousMediaItem()) return
            player.seekToPreviousMediaItem()
            player.playWhenReady = true
            player.play()
            currentPlaylist.getOrNull(player.currentMediaItemIndex)?.id?.let { userAnchorMusicId = it }
            updatePlayerState()
            return
        }
        val currentIndex = resolveUiIndex(player.currentMediaItemIndex)
        val positionMs = if (player.currentMediaItemIndex == currentIndex) {
            player.currentPosition
        } else {
            0L
        }
        if (positionMs > PREVIOUS_RESTART_THRESHOLD_MS) {
            player.seekTo(0L)
            player.playWhenReady = true
            player.play()
            updatePlayerState()
            return
        }
        val prevIndex = when (currentRepeatMode) {
            RepeatMode.SEQUENTIAL -> {
                if (currentIndex <= 0) return
                currentIndex - 1
            }
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL, RepeatMode.RANDOM -> {
                if (currentIndex > 0) currentIndex - 1 else currentPlaylist.lastIndex
            }
        }
        navigateToPlaylistIndex(prevIndex, autoPlay = true)
    }

    override fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        controller?.seekTo(safePosition)
        _currentPosition.value = safePosition
        _playerState.value = _playerState.value.copy(position = safePosition)
        persistPlaybackState(safePosition)
    }

    override fun updatePlaylist(musics: List<Music>, key: String) {
        if (key != playingKey) return
        if (musics.isEmpty()) return
        if (System.currentTimeMillis() - lastUserSetPlaylistTimeMs < 800L) {
            return
        }
        if (isSamePlaylist(musics, currentPlaylist)) {
            if (userPlaylistActive) {
                syncPlaylistForActiveUser(musics)
            }
            return
        }

        if (userPlaylistActive) {
            mergeUserPlaylistFromPaging(musics)
            return
        }

        val state = _playerState.value
        // 分页增量更新：若当前曲目仍在列表中，仅扩展队列，不重置 ExoPlayer
        val currentMusic = state.currentMusic
        if (currentMusic != null && playingKey == key) {
            val currentIdIndex = musics.indexOfFirst { it.id == currentMusic.id }
            if (currentIdIndex >= 0 && musics.size > currentPlaylist.size) {
                currentPlaylist = musics
                controller?.let { player ->
                    if (musics.size > player.mediaItemCount) {
                        for (i in player.mediaItemCount until musics.size) {
                            player.addMediaItem(musics[i].toMediaItem())
                        }
                    }
                }
                _playerState.value = _playerState.value.copy(playlist = musics)
                persistPlaybackState()
                return
            }
        }
        val targetIndex = resolvePlaylistIndex(musics, state.currentMusic, state.currentIndex)
        val keepPosition = musics.getOrNull(targetIndex)?.id == state.currentMusic?.id
        if (targetIndex == state.currentIndex &&
            state.currentMusic?.id == musics.getOrNull(targetIndex)?.id &&
            musics.size >= currentPlaylist.size
        ) {
            currentPlaylist = musics
            _playerState.value = _playerState.value.copy(playlist = musics)
            return
        }
        playingKey = key
        currentPlaylist = musics
        applyPlaylistToPlayer(
            startIndex = targetIndex,
            startPositionMs = if (keepPosition) state.position else 0L,
            autoPlay = state.isPlaying || pendingAutoPlay,
        )
        persistPlaybackState()
    }

    private fun syncPlaylistForActiveUser(musics: List<Music>) {
        val anchorId = userAnchorMusicId ?: _playerState.value.currentMusic?.id ?: return
        val newIndex = musics.indexOfFirst { it.id == anchorId }
        if (newIndex < 0) return
        currentPlaylist = musics
        expectedMediaItemIndex = newIndex
        _playerState.value = _playerState.value.copy(
            playlist = musics,
            currentIndex = newIndex,
            currentMusic = musics[newIndex],
        )
        // 用户点播模式：ExoPlayer 仅单曲，内存歌单由 setPlaylist/navigate 维护，此处不碰队列
    }

    /** 分页列表刷新时合并内存歌单（不切换正在播放的曲目） */
    private fun mergeUserPlaylistFromPaging(musics: List<Music>) {
        val anchorId = userAnchorMusicId ?: _playerState.value.currentMusic?.id ?: return
        val newIndex = musics.indexOfFirst { it.id == anchorId }
        if (newIndex < 0) return
        // 分页快照短暂变短时不要用更少条目覆盖内存歌单（常见于搜索/歌单分页刷新）
        if (musics.size < currentPlaylist.size &&
            currentPlaylist.map { it.id }.containsAll(musics.map { it.id })
        ) {
            return
        }
        currentPlaylist = musics
        expectedMediaItemIndex = newIndex
        _playerState.value = _playerState.value.copy(
            playlist = musics,
            currentIndex = newIndex,
            currentMusic = musics[newIndex],
        )
    }

    override fun setPlaylist(musics: List<Music>, startIndex: Int, key: String) {
        if (musics.isEmpty()) return
        userPlaylistActive = true
        pendingRestorePositionMs = null
        lastUserSetPlaylistTimeMs = System.currentTimeMillis()
        playingKey = key
        currentPlaylist = musics
        pendingAutoPlay = true
        val safeIndex = startIndex.coerceIn(0, musics.lastIndex)
        userAnchorMusicId = musics[safeIndex].id
        publishOptimisticPlayerState(musics, safeIndex)
        applyRepeatModeToPlayer(currentRepeatMode)
        applyTrackToPlayer(
            startIndex = safeIndex,
            startPositionMs = 0L,
            autoPlay = true,
        )
    }

    /** 用户点播：ExoPlayer 只加载当前单曲，完整列表存内存，避免全量 setMediaItems Binder 失败 */
    private fun applyTrackToPlayer(
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean,
    ) {
        if (currentPlaylist.isEmpty()) return
        activePlaylistGeneration++
        applyingPlaylistUntilMs = System.currentTimeMillis() + 1500L
        val safeIndex = startIndex.coerceIn(0, currentPlaylist.lastIndex)
        expectedMediaItemIndex = safeIndex
        pendingAutoPlay = autoPlay
        userAnchorMusicId = currentPlaylist[safeIndex].id
        val music = currentPlaylist[safeIndex]
        val item = music.toMediaItem()
        publishOptimisticPlayerState(currentPlaylist, safeIndex, startPositionMs)
        if (!item.hasPlayableUri()) return
        controller?.run {
            playWhenReady = false
            setMediaItem(item, startPositionMs)
            prepare()
        }
    }

    private fun applyPlaylistToPlayer(
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean,
    ) {
        if (userPlaylistActive) {
            applyTrackToPlayer(startIndex, startPositionMs, autoPlay)
            return
        }
        if (currentPlaylist.isEmpty()) return
        activePlaylistGeneration++
        applyingPlaylistUntilMs = System.currentTimeMillis() + 1500L
        val safeIndex = startIndex.coerceIn(0, currentPlaylist.lastIndex)
        expectedMediaItemIndex = safeIndex
        pendingAutoPlay = autoPlay
        val mediaItems = currentPlaylist.map { it.toMediaItem() }
        controller?.run {
            playWhenReady = false
            setMediaItems(mediaItems, safeIndex, startPositionMs)
            prepare()
        } ?: publishOptimisticPlayerState(currentPlaylist, safeIndex, startPositionMs)
    }

    private fun publishOptimisticPlayerState(
        musics: List<Music>,
        index: Int,
        positionMs: Long = 0L,
    ) {
        val safeIndex = index.coerceIn(0, musics.lastIndex)
        val music = musics[safeIndex]
        val fallbackDurationMs = when {
            music.duration <= 0L -> 0L
            music.duration < 1000L -> music.duration * 1000L
            else -> music.duration
        }
        _playerState.value = _playerState.value.copy(
            currentMusic = music,
            playlist = musics,
            currentIndex = safeIndex,
            position = positionMs,
            duration = fallbackDurationMs,
            repeatMode = currentRepeatMode,
            isFavorite = music.isFavorite,
        )
    }

    private fun resolvePlaylistIndex(
        musics: List<Music>,
        currentMusic: Music?,
        fallbackIndex: Int,
    ): Int {
        if (currentMusic != null) {
            val byId = musics.indexOfFirst { it.id == currentMusic.id }
            if (byId >= 0) return byId
        }
        return fallbackIndex.coerceIn(0, musics.lastIndex)
    }

    private fun isSamePlaylist(a: List<Music>, b: List<Music>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { a[it].id == b[it].id }
    }

    override fun addToQueue(music: Music) {
        if (currentPlaylist.isEmpty()) {
            userPlaylistActive = true
            currentPlaylist = listOf(music)
            userAnchorMusicId = music.id
            publishOptimisticPlayerState(currentPlaylist, 0)
            applyTrackToPlayer(0, 0L, autoPlay = false)
            persistPlaybackState()
            return
        }
        val uiIndex = resolveUiIndex(controller?.currentMediaItemIndex ?: 0)
        val insertIndex = (uiIndex + 1).coerceAtMost(currentPlaylist.size)
        val newPlaylist = currentPlaylist.toMutableList()
        newPlaylist.add(insertIndex, music)
        currentPlaylist = newPlaylist
        _playerState.value = _playerState.value.copy(playlist = newPlaylist)
        if (!userPlaylistActive) {
            controller?.addMediaItem(insertIndex, music.toMediaItem())
        }
        updatePlayerState()
        persistPlaybackState()
    }

    override fun addAllToQueue(musics: List<Music>) {
        if (musics.isEmpty()) return
        if (currentPlaylist.isEmpty()) {
            userPlaylistActive = true
            currentPlaylist = musics.toList()
            userAnchorMusicId = musics.first().id
            publishOptimisticPlayerState(currentPlaylist, 0)
            applyTrackToPlayer(0, 0L, autoPlay = false)
            persistPlaybackState()
            return
        }
        val uiIndex = resolveUiIndex(controller?.currentMediaItemIndex ?: 0)
        val insertIndex = (uiIndex + 1).coerceAtMost(currentPlaylist.size)
        val newPlaylist = currentPlaylist.toMutableList()
        newPlaylist.addAll(insertIndex, musics)
        currentPlaylist = newPlaylist
        _playerState.value = _playerState.value.copy(playlist = newPlaylist)
        if (!userPlaylistActive) {
            musics.forEachIndexed { index, music ->
                controller?.addMediaItem(insertIndex + index, music.toMediaItem())
            }
        }
        updatePlayerState()
        persistPlaybackState()
    }

    override fun setRepeatMode(repeatMode: RepeatMode) {
        currentRepeatMode = repeatMode
        applyRepeatModeToPlayer(repeatMode)
        updatePlayerState()
        scope.launch {
            playbackStateStore.saveRepeatMode(repeatMode)
        }
        persistPlaybackState()
    }

    private fun applyRepeatModeToPlayer(repeatMode: RepeatMode) {
        controller?.let { player ->
            if (userPlaylistActive) {
                // Exo 仅持单曲时 REPEAT_MODE_ALL/ONE 只会循环当前 MediaItem，列表逻辑由应用层处理
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = false
            } else {
                player.repeatMode = when (repeatMode) {
                    RepeatMode.SEQUENTIAL, RepeatMode.RANDOM -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
                player.shuffleModeEnabled = repeatMode == RepeatMode.RANDOM
            }
        }
    }

    /** 单曲 Exo 队列下，曲目播放结束后的列表循环/顺序/随机逻辑 */
    private fun handleUserPlaylistTrackEnded(player: Player) {
        if (currentPlaylist.isEmpty()) return
        val currentIndex = resolveUiIndex(player.currentMediaItemIndex)
        when (currentRepeatMode) {
            RepeatMode.ONE -> applyTrackToPlayer(currentIndex, 0L, autoPlay = true)
            RepeatMode.ALL -> {
                val nextIndex = (currentIndex + 1) % currentPlaylist.size
                navigateToPlaylistIndex(nextIndex, autoPlay = true)
            }
            RepeatMode.SEQUENTIAL -> {
                if (currentIndex >= currentPlaylist.lastIndex) {
                    player.pause()
                    player.playWhenReady = false
                    return
                }
                navigateToPlaylistIndex(currentIndex + 1, autoPlay = true)
            }
            RepeatMode.RANDOM -> {
                val nextIndex = pickRandomNextIndex(currentIndex)
                navigateToPlaylistIndex(nextIndex, autoPlay = true)
            }
        }
    }

    private fun pickRandomNextIndex(currentIndex: Int): Int {
        if (currentPlaylist.size <= 1) return currentIndex
        var next = currentIndex
        while (next == currentIndex) {
            next = Random.nextInt(currentPlaylist.size)
        }
        return next
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override fun removeFromQueue(index: Int) {
        if (index >= 0 && index < currentPlaylist.size) {
            currentPlaylist = currentPlaylist.filterIndexed { i, _ -> i != index }
            controller?.removeMediaItem(index)

            val currentIndex = _playerState.value.currentIndex
            if (index == currentIndex && currentPlaylist.isNotEmpty()) {
                if (currentIndex < currentPlaylist.size) {
                    controller?.seekToDefaultPosition(currentIndex)
                } else {
                    controller?.seekToDefaultPosition(currentPlaylist.size - 1)
                }
            }
            if (currentPlaylist.isEmpty()) {
                playingKey = null
            }
            updatePlayerState()
            persistPlaybackState()
        }
    }

    override fun clearQueue() {
        userPlaylistActive = false
        userAnchorMusicId = null
        currentPlaylist = emptyList()
        controller?.setMediaItems(emptyList())
        playingKey = null
        updatePlayerState()
        scope.launch {
            playbackStateStore.saveSnapshot(
                PlaybackSnapshot(repeatMode = currentRepeatMode)
            )
        }
    }

    override fun seekToQueueItem(index: Int) {
        if (index in currentPlaylist.indices) {
            navigateToPlaylistIndex(index, autoPlay = true)
            persistPlaybackState()
        }
    }

    private fun Music.toMediaItem(): MediaItem {
        // 队列 MediaItem 仅保留播放所需字段，避免歌词等大字段导致 Binder 事务失败
        val builder = MediaItem.Builder()
            .setMediaId(id.toString())
            .apply {
                // 本地文件勿复用在线 cache key，否则 seek 会走 HTTP 上游
                if (!MediaUrlResolver.isLocalMedia(url)) {
                    setCustomCacheKey("cache_$id")
                }
            }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artists.joinToString(", ") { it.name })
                    .setAlbumTitle(album?.title)
                    .apply {
                        coverImage?.let { setArtworkUri(MediaUrlResolver.resolve(it)?.toUri()) }
                    }
                    .build(),
            )
        val resolvedUrl = MediaUrlResolver.resolve(url)
        if (!resolvedUrl.isNullOrBlank()) {
            val uri = resolvedUrl.toUri()
            // MediaController 跨进程时会剥离 localConfiguration，需同时写入 requestMetadata.mediaUri
            builder
                .setUri(uri)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(uri)
                        .build(),
                )
        }
        return builder.build()
    }

    /** 客户端构建的 MediaItem 在跨进程后 localConfiguration 会被清空，以 requestMetadata 为准 */
    private fun MediaItem.hasPlayableUri(): Boolean =
        localConfiguration?.uri != null || requestMetadata.mediaUri != null

    private fun Player.toRepeatMode(): RepeatMode = when {
        shuffleModeEnabled -> RepeatMode.RANDOM
        repeatMode == Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        repeatMode == Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.SEQUENTIAL
    }

    companion object {
        private const val POSITION_PERSIST_INTERVAL_MS = 5_000L
        private const val POSITION_PERSIST_DELTA_MS = 3_000L
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    }
}
