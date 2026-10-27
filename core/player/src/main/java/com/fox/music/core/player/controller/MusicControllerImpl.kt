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
import com.fox.music.core.domain.repository.DownloadRepository
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.music.LyricOverride
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind
import com.fox.music.core.model.music.PlaybackSnapshot
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.model.music.ClipRange
import com.fox.music.core.player.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MusicControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateStore: PlaybackStateStore,
    private val recordPlayUseCase: com.fox.music.core.domain.usecase.RecordPlayUseCase,
    private val downloadRepository: DownloadRepository,
) : MusicController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val localPlayUrls = ConcurrentHashMap<Long, String>()

    /**
     * 音源替换后的 URL 覆盖表（musicId → 新地址）。
     * 列表/队列可能仍持有替换前的旧 Music（含旧 url），播放时优先取本表，
     * 保证同一会话内改过音源的歌曲从新地址播放。
     */
    private val urlOverrides = ConcurrentHashMap<Long, String>()

    /**
     * 本会话用户应用的歌词（musicId → 歌词 + 翻译）。
     * 退出播放页后，列表页会用分页里的曲库歌曲再调 updatePlaylist / setPlaylist，
     * 那些对象仍是旧歌词。有覆盖时固定用覆盖，曲库同步失败也不撤回。
     * 同时写入播放快照，进程被回收后重进同一队列仍能恢复。
     */
    private val lyricOverrides = ConcurrentHashMap<Long, LyricOverride>()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // 上一首歌的播放信息，用于在切歌/暂停时上报实际收听时长
    private var lastTrackId: Long? = null
    private var lastTrackDurationMs: Long = 0L
    private var lastTrackPositionMs: Long = 0L
    @Volatile
    private var restorePlayAfterTransientMedia = false
    /** 避免同一进度重复上报 */
    private var lastReportedPlayKey: String? = null

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var currentPlaylist: List<Music> = emptyList()
    private var playingKey: String? = null
    private var playlistSource: PlaybackPlaylistSource? = null
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

    // ===== 音流模式：高潮片段区间播放 =====
    private var activeClip: ClipRange? = null
    /** activeClip 所属歌曲 id；applyTrackToPlayer 据此判断「是否切了歌」再决定是否重置 clip */
    private var activeClipMusicId: Long? = null
    /** 当前歌曲内的片段游标（0-based），advanceStream 在曲内推进时自增 */
    private var currentClipIndex: Int = 0
    /** 音流队列的片段表：musicId -> 高潮片段列表（来自服务端 /api/highlight/stream） */
    private val streamClips = HashMap<Long, List<ClipRange>>()
    /** advance 单一起发源锁存：轮询检测到片段终点后置位，待 applyTrackToPlayer 复位，
     *  防止「轮询 + STATE_ENDED 回调」双触发跳歌（KAT：音流页 advance 只走轮询） */
    @Volatile
    private var clipAdvanceTriggered = false
    /** 当前片段绝对起点（毫秒）：flushCurrentPlayRecord 计算 clip 收听时长用 */
    private var lastTrackClipStartMs: Long = 0L
    /** 音流模式：当前曲目是否已「放开片段限制」（用户拖到片段外后为 true，则整曲播完再切歌） */
    private var streamClipDisabled = false
    /** 音流模式：片段功能是否开启（仅音流页在前台为 true，由导航层驱动）。
     *  切到其它 Tab 后关闭；它是「粘性」的，切歌时 applyTrackToPlayer 不得把它悄悄恢复。 */
    private var streamClipFeatureEnabled = true

    init {
        connectToService()
        scope.launch(Dispatchers.IO) {
            downloadRepository.downloads.collect { tasks ->
                val next = HashMap<Long, String>()
                tasks.forEach { task ->
                    if (task.status != DownloadStatus.COMPLETED) return@forEach
                    val path = task.filePath ?: return@forEach
                    if (File(path).exists()) {
                        next[task.musicId] = "file://$path"
                    }
                }
                localPlayUrls.keys.retainAll(next.keys)
                localPlayUrls.putAll(next)
            }
        }
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

        val snapshot = playbackStateStore.loadSnapshot()
        installLyricOverrides(snapshot?.lyricOverrides.orEmpty())

        if (shouldSkipRestoreForUserPlaylist()) {
            reapplyLyricOverridesToPlaylist()
            applyUserPlaylistOnConnect()
            hasRestored = true
            return
        }

        isRestoring = true
        try {
            if (shouldSkipRestoreForUserPlaylist()) {
                reapplyLyricOverridesToPlaylist()
                applyUserPlaylistOnConnect()
                return
            }
            if (snapshot != null && snapshot.isValid) {
                restoreFromSnapshot(snapshot)
            } else {
                reapplyLyricOverridesToPlaylist()
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
        installLyricOverrides(snapshot.lyricOverrides)
        playingKey = snapshot.playingKey
        playlistSource = snapshot.playlistSource
        currentPlaylist = snapshot.playlist.applyingLyricOverrides()
        currentRepeatMode = snapshot.repeatMode
        pendingAutoPlay = false

        val safeIndex = snapshot.currentIndex.coerceIn(0, snapshot.playlist.lastIndex)
        userAnchorMusicId = snapshot.playlist.getOrNull(safeIndex)?.id
        expectedMediaItemIndex = safeIndex
        userPlaylistActive = true
        applyRepeatModeToPlayer(snapshot.repeatMode)
        // 冷启动仅恢复 UI/内存状态，不加载 ExoPlayer 音频，避免与用户点播争用队列
        pendingRestorePositionMs = snapshot.positionMs.coerceAtLeast(0L)
        publishOptimisticPlayerState(
            musics = snapshot.playlist,
            index = safeIndex,
            positionMs = pendingRestorePositionMs!!,
        )
        _currentPosition.value = pendingRestorePositionMs!!

        // 将当前曲目元数据写入 ExoPlayer → MediaSession，使系统（通知栏、蓝牙、锁屏）
        // 在冷启动未播放时即可看到音乐信息；不调用 prepare()，不加载音频。
        val music = currentPlaylist.getOrNull(safeIndex) ?: return
        controller?.run {
            playWhenReady = false
            setMediaItem(music.toMediaItem())
        }
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState()
                if (!isPlaying && !isRestoring) {
                    persistPlaybackState()
                    // 用户主动暂停时上报当前收听时长（缓冲导致的短暂停顿不报）
                    val player = controller
                    if (player != null && !player.playWhenReady) {
                        flushCurrentPlayRecord()
                    }
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
                        // 音流模式：用户已放开片段限制（或片段功能已关闭）→ 本曲自然播完即推进下一首。
                        // 片段内结束不会走到这里（轮询已在 clip.endMs 处 advance 并复位该标志）。
                        if (activeClip != null &&
                            (streamClipDisabled || !streamClipFeatureEnabled)
                        ) {
                            advanceStream()
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

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                timber.log.Timber.w(error, "Playback error")
                val state = _playerState.value
                _playerState.value = state.copy(
                    isLoading = false,
                    isPlaying = false,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                flushCurrentPlayRecord()

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
                lastReportedPlayKey = null

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
                        val position = player.currentPosition
                        if (_currentPosition.value != position) {
                            _currentPosition.value = position
                        }
                        lastTrackPositionMs = position
                        if (player.duration > 0) {
                            lastTrackDurationMs = player.duration
                        }
                        // 仅同步 position 到 PlayerState，避免每 500ms 整对象重建驱动全树重组
                        val state = _playerState.value
                        if (state.position != position ||
                            (player.duration > 0 && state.duration != player.duration)
                        ) {
                            _playerState.value = state.copy(
                                position = position,
                                duration = if (player.duration > 0) player.duration else state.duration,
                                isPlaying = player.isPlaying,
                                isLoading = player.playbackState == Player.STATE_BUFFERING,
                                clip = activeClip,
                            )
                        }
                        // 音流模式：播放到高潮片段终点 → 轮询单一起发源推进（防 STATE_ENDED 双触发跳歌）。
                        // 用户拖到片段外后 streamClipDisabled 为 true，或片段功能已关闭（切到其它 Tab），
                        // 此处都不再推进，交由媒体自然结束处理（见 STATE_ENDED 分支）。
                        if (activeClip != null && !streamClipDisabled && streamClipFeatureEnabled &&
                            position >= activeClip!!.endMs && !clipAdvanceTriggered
                        ) {
                            clipAdvanceTriggered = true
                            advanceStream()
                        }
                    }
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
            playlistSource = playlistSource,
            lyricOverrides = lyricOverrides.toSortedMap(),
        )
    }

    private fun isApplyingPlaylist(): Boolean =
        System.currentTimeMillis() < applyingPlaylistUntilMs

    private fun flushCurrentPlayRecord() {
        val trackId = lastTrackId ?: return
        if (trackId <= 0) return
        // 音流模式：收听时长按「片段内相对进度」计算，progressPercent 传 null（避免污染 avg_play_progress）
        val listenedMs = if (activeClip != null) {
            (lastTrackPositionMs - lastTrackClipStartMs).coerceAtLeast(0L)
        } else {
            when {
                lastTrackPositionMs <= 0L -> 0L
                lastTrackDurationMs > 0L -> lastTrackPositionMs.coerceAtMost(lastTrackDurationMs)
                else -> lastTrackPositionMs
            }
        }
        val durationSec = (listenedMs / 1000L).toInt().takeIf { it > 0 } ?: return
        val progress = if (activeClip != null) {
            null
        } else if (lastTrackDurationMs > 0) {
            ((lastTrackPositionMs * 100) / lastTrackDurationMs).toInt().coerceIn(0, 100)
        } else {
            null
        }
        val reportKey = "$trackId:$durationSec:$progress"
        if (reportKey == lastReportedPlayKey) return
        lastReportedPlayKey = reportKey
        scope.launch {
            recordPlayUseCase(trackId, durationSec, progress)
        }
    }

    private fun persistPlaybackState(positionOverrideMs: Long? = null, force: Boolean = false) {
        if (!force && isApplyingPlaylist()) return
        val snapshot = buildSnapshot(positionOverrideMs) ?: return
        lastPersistedPositionMs = snapshot.positionMs
        lastPersistTimeMs = System.currentTimeMillis()
        scope.launch {
            playbackStateStore.saveSnapshot(snapshot)
        }
    }

    override fun flushPlaybackState() {
        val snapshot = buildSnapshot() ?: return
        lastPersistedPositionMs = snapshot.positionMs
        lastPersistTimeMs = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            playbackStateStore.saveSnapshot(snapshot)
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
        val playlistForUi = currentPlaylist.applyingLyricOverrides()
        if (playlistForUi !== currentPlaylist) {
            currentPlaylist = playlistForUi
        }
        val currentMusic = playlistForUi.getOrNull(currentIndex)
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
            playlistSource = playlistSource,
            // 必须带上当前片段：这里是**整体重建** PlayerState（不是 copy），
            // 漏一个字段就等于把它抹成默认值。此前漏了 clip，导致 ExoPlayer 每次回调
            // （isPlaying / buffering / mediaItemTransition …）都把高潮片段抹成 null，
            // 直到 500ms 位置轮询再补回来 —— 表现就是「高潮进度节点消失一下又回来」。
            clip = activeClip,
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

    override fun updateMusicLyrics(musicId: Long, lyrics: String?, lyricsTrans: String?) {
        if (lyrics.isNullOrBlank()) return
        val resolvedTrans = lyricsTrans ?: lyricsTransOf(musicId)
        lyricOverrides[musicId] = LyricOverride(lyrics = lyrics, lyricsTrans = resolvedTrans)
        writeLyrics(musicId, lyrics, resolvedTrans)
        // 应用歌词发生在切歌窗口内时也要落盘，退出播放页再进不能丢
        persistPlaybackState(force = true)
    }

    /** 预取补全：已有歌词或已有覆盖时不写，也不记入覆盖表。 */
    fun fillMusicLyricsIfAbsent(musicId: Long, lyrics: String?, lyricsTrans: String?) {
        if (lyrics.isNullOrBlank()) return
        if (lyricOverrides.containsKey(musicId)) return
        if (!lyricsOf(musicId).isNullOrBlank()) return
        writeLyrics(musicId, lyrics, lyricsTrans ?: lyricsTransOf(musicId))
        persistPlaybackState(force = true)
    }

    fun hasLyricOverride(musicId: Long): Boolean = lyricOverrides.containsKey(musicId)

    private fun writeLyrics(musicId: Long, lyrics: String, lyricsTrans: String?) {
        currentPlaylist = currentPlaylist.map { music ->
            if (music.id == musicId) {
                music.copy(lyrics = lyrics, lyricsTrans = lyricsTrans)
            } else {
                music
            }
        }
        val state = _playerState.value
        val updatedMusic = state.currentMusic?.takeIf { it.id == musicId }?.let {
            it.copy(lyrics = lyrics, lyricsTrans = lyricsTrans)
        }
        if (updatedMusic != null) {
            _playerState.value = state.copy(
                currentMusic = updatedMusic,
                playlist = currentPlaylist,
            )
        } else if (state.playlist !== currentPlaylist) {
            _playerState.value = state.copy(playlist = currentPlaylist)
        }
    }

    private fun lyricsOf(musicId: Long): String? =
        currentPlaylist.firstOrNull { it.id == musicId }?.lyrics
            ?: _playerState.value.currentMusic?.takeIf { it.id == musicId }?.lyrics

    private fun lyricsTransOf(musicId: Long): String? =
        currentPlaylist.firstOrNull { it.id == musicId }?.lyricsTrans
            ?: _playerState.value.currentMusic?.takeIf { it.id == musicId }?.lyricsTrans

    private fun installLyricOverrides(saved: Map<Long, LyricOverride>) {
        if (saved.isEmpty()) return
        saved.forEach { (id, override) ->
            if (override.lyrics.isBlank()) return@forEach
            // 内存已有的是本会话刚应用的，不用磁盘里更早的一份盖掉
            lyricOverrides.putIfAbsent(id, override)
        }
    }

    private fun reapplyLyricOverridesToPlaylist() {
        if (lyricOverrides.isEmpty() || currentPlaylist.isEmpty()) return
        val pinned = currentPlaylist.applyingLyricOverrides()
        if (pinned === currentPlaylist) return
        currentPlaylist = pinned
        val state = _playerState.value
        val currentId = state.currentMusic?.id
        val current = currentId?.let { id -> pinned.firstOrNull { it.id == id } } ?: state.currentMusic
        _playerState.value = state.copy(
            currentMusic = current,
            playlist = pinned,
        )
    }

    private fun Music.applyingLyricOverride(): Music {
        val override = lyricOverrides[id] ?: return this
        if (override.lyrics.isBlank()) return this
        if (lyrics == override.lyrics && lyricsTrans == override.lyricsTrans) return this
        return copy(lyrics = override.lyrics, lyricsTrans = override.lyricsTrans)
    }

    private fun List<Music>.applyingLyricOverrides(): List<Music> {
        if (lyricOverrides.isEmpty()) return this
        var changed = false
        val mapped = ArrayList<Music>(size)
        for (music in this) {
            val next = music.applyingLyricOverride()
            if (next !== music) changed = true
            mapped.add(next)
        }
        return if (changed) mapped else this
    }

    override fun updateMusicUrl(musicId: Long, url: String) {
        if (url.isBlank()) return
        // 覆盖表优先于 Music 自带 url：列表/队列可能仍是替换前的旧 Music
        urlOverrides[musicId] = url
        currentPlaylist = currentPlaylist.map { music ->
            if (music.id == musicId && music.url != url) music.copy(url = url) else music
        }
        val state = _playerState.value
        if (state.currentMusic?.id == musicId) {
            _playerState.value = state.copy(
                currentMusic = state.currentMusic?.copy(url = url),
                playlist = currentPlaylist,
            )
            // 当前曲目正挂着旧地址：按新地址重建媒体项，保持进度与播放态
            val index = currentPlaylist.indexOfFirst { it.id == musicId }
            if (index >= 0) {
                applyTrackToPlayer(
                    startIndex = index,
                    startPositionMs = controller?.currentPosition ?: 0L,
                    autoPlay = controller?.isPlaying == true,
                )
            }
        } else {
            _playerState.value = state.copy(playlist = currentPlaylist)
        }
        persistPlaybackState()
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
                (player.mediaItemCount == 0 || !isCurrentTrackAligned(player) || pendingRestorePositionMs != null)
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

    override fun beginTransientMediaPlayback() {
        if (restorePlayAfterTransientMedia) return
        restorePlayAfterTransientMedia = _playerState.value.isPlaying
        if (restorePlayAfterTransientMedia) {
            pause()
        }
    }

    override fun endTransientMediaPlayback() {
        if (!restorePlayAfterTransientMedia) return
        restorePlayAfterTransientMedia = false
        play()
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

    // KAT：音流页「上滑/上一首」请用 seekToQueueItem(index-1)（会挂载该歌首片段），
    // 不要用 previous() —— previous() 在 clip 起点（如 60s）会因 position>3s 永远重播当前片段。
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

    /**
     * 音流模式自由拖拽：
     * - 目标落在片段区间外（含拖过头）→ 放开限制，本曲完整播完再切歌；
     * - 目标落回片段区间内 → 复位限制与 advance 锁存，恢复「到片段终点即切歌」。
     * 非音流（无激活片段）不改变任何行为，等同 seekTo。
     */
    override fun seekFree(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        val clip = activeClip
        val insideClip = clip != null && safePosition >= clip.startMs && safePosition < clip.endMs
        streamClipDisabled = clip != null && !insideClip
        if (!streamClipDisabled) {
            clipAdvanceTriggered = false
        }
        seekTo(safePosition)
    }

    override fun setStreamClipEnabled(enabled: Boolean) {
        streamClipFeatureEnabled = enabled
        if (!enabled) {
            // 离开音流页：什么都不改片段「区间」，只放开限制，让本曲完整播放到自然结束。
            streamClipDisabled = true
        } else {
            // 重新进入音流页：若当前进度已越过本曲片段终点，保持本曲整曲播放（置 advance 锁存，
            // 避免瞬间跳回片段或立刻跳歌），仅从下一首起恢复「只播高潮即切歌」。
            val clip = activeClip
            if (clip != null && _playerState.value.position >= clip.endMs) {
                clipAdvanceTriggered = true
                streamClipDisabled = true
            } else {
                streamClipDisabled = false
            }
        }
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
                val mergedPlaylist = mergePlaylistPreservingDetails(currentPlaylist, musics)
                currentPlaylist = mergedPlaylist
                controller?.let { player ->
                    if (mergedPlaylist.size > player.mediaItemCount) {
                        for (i in player.mediaItemCount until mergedPlaylist.size) {
                            player.addMediaItem(mergedPlaylist[i].toMediaItem())
                        }
                    }
                }
                _playerState.value = _playerState.value.copy(playlist = mergedPlaylist)
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
            val mergedPlaylist = mergePlaylistPreservingDetails(currentPlaylist, musics)
            currentPlaylist = mergedPlaylist
            _playerState.value = _playerState.value.copy(playlist = mergedPlaylist)
            return
        }
        playingKey = key
        currentPlaylist = musics.applyingLyricOverrides()
        applyPlaylistToPlayer(
            startIndex = targetIndex,
            startPositionMs = if (keepPosition) state.position else 0L,
            autoPlay = state.isPlaying || pendingAutoPlay,
        )
        persistPlaybackState()
    }

    override fun syncPlaylistForSource(kind: PlaybackSourceKind, musics: List<Music>) {
        if (musics.isEmpty()) return
        // 只同步与当前播放队列同板块的列表，避免把别的来源的队列换掉
        if (playlistSource?.kind != kind) return
        val key = playingKey ?: return
        // 复用分页合并逻辑：按当前曲目锚定，只更新内存歌单，不打断正在播放的音频
        updatePlaylist(musics, key)
    }

    private fun syncPlaylistForActiveUser(musics: List<Music>) {
        val anchorId = userAnchorMusicId ?: _playerState.value.currentMusic?.id ?: return
        val newIndex = musics.indexOfFirst { it.id == anchorId }
        if (newIndex < 0) return
        val mergedPlaylist = mergePlaylistPreservingDetails(currentPlaylist, musics)
        currentPlaylist = mergedPlaylist
        expectedMediaItemIndex = newIndex
        _playerState.value = _playerState.value.copy(
            playlist = mergedPlaylist,
            currentIndex = newIndex,
            currentMusic = mergedPlaylist[newIndex],
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
        val mergedPlaylist = mergePlaylistPreservingDetails(currentPlaylist, musics)
        currentPlaylist = mergedPlaylist
        expectedMediaItemIndex = newIndex
        _playerState.value = _playerState.value.copy(
            playlist = mergedPlaylist,
            currentIndex = newIndex,
            currentMusic = mergedPlaylist[newIndex],
        )
    }

    private fun mergePlaylistPreservingDetails(
        existing: List<Music>,
        incoming: List<Music>,
    ): List<Music> {
        val existingById = existing.associateBy { it.id }
        return incoming.map { music ->
            mergeMusicPreservingLyrics(music, existingById[music.id])
        }
    }

    private fun mergeMusicPreservingLyrics(incoming: Music, previous: Music?): Music {
        return mergeMusicKeepingLyricOverride(
            incoming = incoming,
            previous = previous,
            override = lyricOverrides[incoming.id],
        )
    }

    override fun setPlaylist(
        musics: List<Music>,
        startIndex: Int,
        key: String,
        source: PlaybackPlaylistSource?,
    ) {
        if (musics.isEmpty()) return
        // 普通点播：退出音流片段态
        activeClip = null
        activeClipMusicId = null
        streamClips.clear()
        currentClipIndex = 0
        streamClipDisabled = false
        userPlaylistActive = true
        pendingRestorePositionMs = null
        lastUserSetPlaylistTimeMs = System.currentTimeMillis()
        playingKey = key
        playlistSource = source
        val lyricLookup = buildList {
            addAll(currentPlaylist)
            _playerState.value.currentMusic?.let { add(it) }
        }.distinctBy { it.id }
        val mergedPlaylist = mergePlaylistPreservingDetails(lyricLookup, musics)
        currentPlaylist = mergedPlaylist
        pendingAutoPlay = true
        val safeIndex = startIndex.coerceIn(0, mergedPlaylist.lastIndex)
        userAnchorMusicId = mergedPlaylist[safeIndex].id
        publishOptimisticPlayerState(mergedPlaylist, safeIndex)
        applyRepeatModeToPlayer(currentRepeatMode)
        applyTrackToPlayer(
            startIndex = safeIndex,
            startPositionMs = 0L,
            autoPlay = true,
        )
        persistPlaybackState()
    }

    /**
     * 音流模式入口：按服务端排好的队列启动刷歌，每个片段一个「高潮单元」。
     * 与 setPlaylist 的差异：setMediaItem 传 clip.startMs 绝对起点，轮询到 clip.endMs 自动 advance。
     */
    override fun startStreamQueue(
        musics: List<Music>,
        clipsByMusic: Map<Long, List<ClipRange>>,
        startIndex: Int,
        key: String,
        source: PlaybackPlaylistSource?,
    ) {
        if (musics.isEmpty()) return
        userPlaylistActive = true
        pendingRestorePositionMs = null
        lastUserSetPlaylistTimeMs = System.currentTimeMillis()
        playingKey = key
        playlistSource = source
        streamClips.clear()
        // 一首歌可能返回多个高潮片段，音流口径只取第一个播放
        streamClips.putAll(clipsByMusic.mapValues { it.value.take(1) })
        currentPlaylist = musics.applyingLyricOverrides()
        val safeIndex = startIndex.coerceIn(0, currentPlaylist.lastIndex)
        userAnchorMusicId = currentPlaylist[safeIndex].id
        val firstClips = clipsByMusic[currentPlaylist[safeIndex].id]
        activeClip = firstClips?.firstOrNull() ?: fallbackClip(currentPlaylist[safeIndex])
        activeClipMusicId = currentPlaylist[safeIndex].id
        currentClipIndex = 0
        applyRepeatModeToPlayer(currentRepeatMode)
        applyTrackToPlayer(safeIndex, 0L, autoPlay = true)
        persistPlaybackState()
    }

    /**
     * 「上滑加载更多」：把下一批歌挂到音流队列尾部。
     *
     * 刻意不走 [applyTrackToPlayer]：音流一次只向 ExoPlayer 加载当前一轨，队列只是内存列表，
     * 因此追加只改内存 + 广播 playlist，当前片段的播放位置与片段态完全不受影响。
     */
    override fun appendStreamQueue(
        musics: List<Music>,
        clipsByMusic: Map<Long, List<ClipRange>>,
    ): Boolean {
        // 非音流会话（普通歌单）不接管：调用方应回退到 addAllToQueue
        if (streamClips.isEmpty()) return false
        val existing = currentPlaylist.mapTo(HashSet()) { it.id }
        // 同批内也可能重复（服务端补齐逻辑），故按顺序去重
        val seen = HashSet<Long>()
        val fresh = musics.filter { m -> m.id !in existing && seen.add(m.id) }
            .applyingLyricOverrides()
        if (fresh.isEmpty()) return false
        // 与 startStreamQueue 同口径：一首歌只播第一个片段
        for (m in fresh) {
            clipsByMusic[m.id]?.take(1)?.let { streamClips[m.id] = it }
        }
        currentPlaylist = currentPlaylist + fresh
        // 只广播队列变化：不动 currentMusic/currentIndex/position，避免打断播放与 UI 抖动
        _playerState.value = _playerState.value.copy(playlist = currentPlaylist)
        persistPlaybackState()
        return true
    }

    /**
     * 音流模式推进：当前歌还有剩余片段 → 切到下一片段；否则切下一首并挂载其首片段。
     * 队尾行为：SEQUENTIAL 不续拉（pause 停住，依赖 next() 会 no-op 故这里主动 pause）；
     * ALL/RANDOM 续播首首。advance 唯一由轮询调用（见 startPositionUpdates）。
     */
    override fun advanceStream() {
        val player = controller ?: return
        if (currentPlaylist.isEmpty() || activeClip == null) return
        val curIdx = resolveUiIndex(player.currentMediaItemIndex)
        val song = currentPlaylist.getOrNull(curIdx) ?: return
        val songClips = streamClips[song.id]
        if (songClips != null && currentClipIndex < songClips.lastIndex) {
            // 同一首歌的下一个高潮片段
            currentClipIndex++
            activeClip = songClips[currentClipIndex]
            activeClipMusicId = song.id
            applyTrackToPlayer(curIdx, 0L, autoPlay = true)
        } else {
            // 切到下一首
            if (curIdx >= currentPlaylist.lastIndex &&
                currentRepeatMode == RepeatMode.SEQUENTIAL
            ) {
                player.pause()
                return
            }
            val nextIdx = (curIdx + 1) % currentPlaylist.size
            val nextSong = currentPlaylist[nextIdx]
            val nextClips = streamClips[nextSong.id]
            activeClip = nextClips?.firstOrNull() ?: fallbackClip(nextSong)
            activeClipMusicId = nextSong.id
            currentClipIndex = 0
            applyTrackToPlayer(nextIdx, 0L, autoPlay = true)
        }
    }

    /** 无高潮片段时的兜底 clip：取曲长前 30% 起、长 60s（与 feature/stream-android StreamClipPolicy 同口径，对齐服务端 60s 片段） */
    private fun fallbackClip(music: Music): ClipRange {
        val dur = when {
            music.duration <= 0L -> 210_000L
            music.duration < 1000L -> music.duration * 1000L
            else -> music.duration
        }
        val safeDur = dur.coerceIn(30_000L, 600_000L)
        val start = ((safeDur * 0.3).toLong()).coerceAtMost((safeDur - 60_000L).coerceAtLeast(0L))
        val end = (start + 60_000L).coerceAtMost(safeDur)
        return ClipRange(start, end)
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
        // 音流模式：切到新歌或无激活片段时，按片段表（或兜底）挂载 clip。
        // 仅音流队列会话（streamClips 非空，由 startStreamQueue 建立、setPlaylist 清空）才挂片段；
        // 普通点播若走兜底片段会从「曲长前 30%」起播，因此必须保持 activeClip 为空、从 0 起播。
        if (streamClips.isNotEmpty() && (activeClip == null || activeClipMusicId != music.id)) {
            val tc = streamClips[music.id]
            activeClip = tc?.firstOrNull() ?: fallbackClip(music)
            activeClipMusicId = music.id
            currentClipIndex = 0
        }
        // A 方案：setMediaItem 传 clip 绝对起点，歌词/快照/恢复零改动
        val clipStart = activeClip?.startMs ?: startPositionMs
        lastTrackClipStartMs = clipStart
        clipAdvanceTriggered = false
        // 切歌（advance/用户翻页均经此）即复位片段限制，恢复「只播高潮」语义；
        // 但若片段功能已关闭（已切到非音流 Tab），复位目标为「禁用」，避免片段模式在后台复活。
        streamClipDisabled = !streamClipFeatureEnabled
        val item = music.toMediaItem()
        publishOptimisticPlayerState(currentPlaylist, safeIndex, clipStart)
        if (!item.hasPlayableUri()) return
        controller?.run {
            playWhenReady = false
            setMediaItem(item, clipStart)
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
            playlistSource = playlistSource,
            clip = activeClip,
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
            val pinned = music.applyingLyricOverride()
            currentPlaylist = listOf(pinned)
            userAnchorMusicId = pinned.id
            publishOptimisticPlayerState(currentPlaylist, 0)
            applyTrackToPlayer(0, 0L, autoPlay = false)
            persistPlaybackState()
            return
        }
        val uiIndex = resolveUiIndex(controller?.currentMediaItemIndex ?: 0)
        val insertIndex = (uiIndex + 1).coerceAtMost(currentPlaylist.size)
        val newPlaylist = currentPlaylist.toMutableList()
        newPlaylist.add(insertIndex, music.applyingLyricOverride())
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
            val pinned = musics.applyingLyricOverrides()
            currentPlaylist = pinned
            userAnchorMusicId = pinned.first().id
            publishOptimisticPlayerState(currentPlaylist, 0)
            applyTrackToPlayer(0, 0L, autoPlay = false)
            persistPlaybackState()
            return
        }
        val uiIndex = resolveUiIndex(controller?.currentMediaItemIndex ?: 0)
        val insertIndex = (uiIndex + 1).coerceAtMost(currentPlaylist.size)
        val pinnedIncoming = musics.applyingLyricOverrides()
        val newPlaylist = currentPlaylist.toMutableList()
        newPlaylist.addAll(insertIndex, pinnedIncoming)
        currentPlaylist = newPlaylist
        _playerState.value = _playerState.value.copy(playlist = newPlaylist)
        if (!userPlaylistActive) {
            pinnedIncoming.forEachIndexed { index, music ->
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
        if (index < 0 || index >= currentPlaylist.size) return

        val wasPlaying = _playerState.value.isPlaying
        val currentIndex = _playerState.value.currentIndex
        val removingCurrent = index == currentIndex

        currentPlaylist = currentPlaylist.filterIndexed { i, _ -> i != index }

        if (currentPlaylist.isEmpty()) {
            playingKey = null
            playlistSource = null
            userAnchorMusicId = null
            expectedMediaItemIndex = null
            controller?.setMediaItems(emptyList())
            updatePlayerState()
            persistPlaybackState()
            return
        }

        if (removingCurrent) {
            // 删的是当前曲：切到原下一首（同 index 顶上来），末尾则回退到最后一首
            val nextIndex = if (currentIndex < currentPlaylist.size) {
                currentIndex
            } else {
                currentPlaylist.lastIndex
            }
            navigateToPlaylistIndex(nextIndex, autoPlay = wasPlaying)
            persistPlaybackState()
            return
        }

        if (userPlaylistActive) {
            if (index < currentIndex) {
                expectedMediaItemIndex = (currentIndex - 1).coerceAtLeast(0)
            }
        } else {
            controller?.removeMediaItem(index)
        }
        updatePlayerState()
        persistPlaybackState()
    }

    override fun removeTracksFromPlaylistQueue(
        musicIds: Collection<Long>,
        playlistKey: String,
        playlistId: Long,
    ) {
        if (musicIds.isEmpty() || currentPlaylist.isEmpty() || playlistId <= 0L) return
        val removeIds = musicIds.toSet()
        if (currentPlaylist.none { it.id in removeIds }) return
        if (!playbackTargetsPlaylist(
                playingKey = playingKey,
                playlistKey = playlistKey,
                sourceKind = playlistSource?.kind,
                sourceId = playlistSource?.id,
                playlistId = playlistId,
            )
        ) {
            return
        }

        val anchoredIndex = _playerState.value.currentMusic?.id?.let { id ->
            currentPlaylist.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: _playerState.value.currentIndex
        val plan = planQueueRemoval(currentPlaylist.size, anchoredIndex) { index ->
            currentPlaylist[index].id in removeIds
        }
        val wasPlaying = _playerState.value.isPlaying
        val oldPlaylist = currentPlaylist
        removeIds.forEach { id ->
            streamClips.remove(id)
            if (activeClipMusicId == id) {
                activeClip = null
                activeClipMusicId = null
                currentClipIndex = 0
            }
        }

        val player = controller
        val exoAligned = player != null && !userPlaylistActive && isExoQueueAligned(player)
        if (exoAligned && player != null) {
            for (index in oldPlaylist.indices.reversed()) {
                if (oldPlaylist[index].id in removeIds && index < player.mediaItemCount) {
                    player.removeMediaItem(index)
                }
            }
        }

        if (plan.nextIndex < 0) {
            currentPlaylist = emptyList()
            playingKey = null
            playlistSource = null
            userAnchorMusicId = null
            expectedMediaItemIndex = null
            activeClip = null
            activeClipMusicId = null
            streamClips.clear()
            currentClipIndex = 0
            player?.setMediaItems(emptyList())
            if (player != null) {
                updatePlayerState()
            } else {
                _playerState.value = _playerState.value.copy(
                    currentMusic = null,
                    playlist = emptyList(),
                    currentIndex = 0,
                    isPlaying = false,
                    clip = null,
                )
            }
            persistPlaybackState()
            return
        }

        currentPlaylist = plan.keptOriginalIndices.map { oldPlaylist[it] }
        if (plan.removedCurrent) {
            navigateToPlaylistIndex(plan.nextIndex, autoPlay = wasPlaying)
            persistPlaybackState()
            return
        }

        val keptIndex = plan.nextIndex
        userAnchorMusicId = currentPlaylist[keptIndex].id
        expectedMediaItemIndex = keptIndex
        when {
            player == null -> publishOptimisticPlayerState(
                currentPlaylist,
                keptIndex,
                _playerState.value.position,
            )
            !userPlaylistActive && !exoAligned -> applyPlaylistToPlayer(
                keptIndex,
                _playerState.value.position,
                wasPlaying,
            )
            else -> updatePlayerState()
        }
        persistPlaybackState()
    }

    override fun clearQueue() {
        userPlaylistActive = false
        // 退出音流片段态
        activeClip = null
        activeClipMusicId = null
        streamClips.clear()
        currentClipIndex = 0
        streamClipDisabled = false
        userAnchorMusicId = null
        currentPlaylist = emptyList()
        controller?.setMediaItems(emptyList())
        playingKey = null
        playlistSource = null
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

    /**
     * 队列内拖动排序（播放列表页长按拖拽）。
     *
     * - 内存歌单重排后，当前播放项按三种情况换算新下标：拖自己 → 新位；
     *   被跨越区间整体平移一格；其余不动。
     * - anchor 仍按 mediaId 定位（resolveUiIndex 优先 indexOfFirst(anchorId)），
     *   所以 updatePlayerState 自然解析到新下标；
     * - Exo 对齐队列（非用户点播模式）同步 moveMediaItem，保证「下一曲」按新顺序走。
     */
    override fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return
        if (from < 0 || from >= currentPlaylist.size) return
        if (to < 0 || to >= currentPlaylist.size) return

        val oldIndex = _playerState.value.currentIndex
        val list = currentPlaylist.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        currentPlaylist = list

        val newCurrentIndex = when {
            oldIndex == from -> to
            from < to && oldIndex in (from + 1)..to -> oldIndex - 1
            from > to && oldIndex in to until from -> oldIndex + 1
            else -> oldIndex
        }.coerceIn(0, currentPlaylist.lastIndex)

        _playerState.value.currentMusic?.id?.let { userAnchorMusicId = it }
        expectedMediaItemIndex = newCurrentIndex
        val player = controller
        if (player != null && !userPlaylistActive && isExoQueueAligned(player)) {
            player.moveMediaItem(from, to)
        }
        updatePlayerState()
        persistPlaybackState()
    }

    private fun Music.resolvePlayUrl(): String =
        localPlayUrls[id] ?: urlOverrides[id] ?: url

    private fun Music.toMediaItem(): MediaItem {
        // 队列 MediaItem 仅保留播放所需字段，避免歌词等大字段导致 Binder 事务失败
        val playUrl = resolvePlayUrl()
        val resolvedUrl = MediaUrlResolver.resolve(playUrl)
        val builder = MediaItem.Builder()
            .setMediaId(id.toString())
            .apply {
                // 本地文件勿复用在线 cache key，否则 seek 会走 HTTP 上游。
                // 在线缓存键必须带音源地址指纹：只按 id 建键时，音源替换后 URL 变了仍会命中
                // 替换前的旧音频字节（表现为「已替换但播放还是旧音频」，清应用数据才好）。
                if (!MediaUrlResolver.isLocalMedia(playUrl)) {
                    setCustomCacheKey("cache_${id}_${resolvedUrl.orEmpty().hashCode()}")
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
