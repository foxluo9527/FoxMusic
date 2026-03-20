package com.fox.music.core.player.controller

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.player.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MusicControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MusicController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var currentPlaylist: List<Music> = emptyList()

    private var playingKey: String? = null

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
                        },
                        MoreExecutors.directExecutor()
                    )
                }
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    controller?.play()
                }
                updatePlayerState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePlayerState()
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
                    _currentPosition.value = player.currentPosition
                    // Also update position in player state
                    _playerState.value = _playerState.value.copy(
                        position = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0),
                        playlist = currentPlaylist,
                        currentIndex = player.currentMediaItemIndex
                    )
                }
                delay(500L)
            }
        }
    }

    private fun updatePlayerState() {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        val currentMusic = currentPlaylist.getOrNull(currentIndex)
        _playerState.value = PlayerState(
            currentMusic = currentMusic,
            playlist = currentPlaylist,
            currentIndex = currentIndex,
            isPlaying = player.isPlaying,
            isLoading = player.playbackState == Player.STATE_BUFFERING,
            position = player.currentPosition,
            duration = player.duration.coerceAtLeast(0),
            repeatMode = player.repeatMode.toRepeatMode(),
            shuffleMode = player.shuffleModeEnabled,
            playbackSpeed = player.playbackParameters.speed
        )
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
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
    }

    override fun next() {
        val player = controller ?: return
        player.seekToNextMediaItem()
        if (! player.isPlaying) {
            player.play()
        }
    }

    override fun previous() {
        val player = controller ?: return
        player.seekToPreviousMediaItem()
        if (! player.isPlaying) {
            player.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun updatePlaylist(musics: List<Music>, key: String) {
        if (key != playingKey) {
            return
        }
        _playerState.value.let {state ->
            playingKey = key
            currentPlaylist = musics
            val mediaItems = musics.map {it.toMediaItem()}
            controller?.run {
                setMediaItems(
                    mediaItems,
                    state.currentIndex,
                    if (musics[state.currentIndex].url == state.currentMusic?.url) state.position else 0L
                )
                prepare()
            }
            updatePlayerState()
        }
    }

    override fun setPlaylist(musics: List<Music>, startIndex: Int, key: String) {
        playingKey = key
        currentPlaylist = musics
        val mediaItems = musics.map { it.toMediaItem() }
        controller?.run {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
        }
        updatePlayerState()
    }

    override fun addToQueue(music: Music) {
        val newPlaylist = currentPlaylist.toMutableList()
        val currentPosition = currentPosition.value
        if (currentPosition == 0L) {
            newPlaylist.add(0, music)
        } else {
            newPlaylist.add(currentPosition.toInt() + 1, music)
        }
        currentPlaylist = newPlaylist
        controller?.addMediaItem(music.toMediaItem())
        updatePlayerState()
    }

    override fun setRepeatMode(repeatMode: RepeatMode) {
        controller?.let {
            it.repeatMode = when (repeatMode) {
                RepeatMode.RANDOM -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
            it.shuffleModeEnabled = repeatMode == RepeatMode.RANDOM
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override fun removeFromQueue(index: Int) {
        if (index >= 0 && index < currentPlaylist.size) {
            currentPlaylist = currentPlaylist.filterIndexed { i, _ -> i != index }
            controller?.removeMediaItem(index)

            // 如果删除的是当前播放的歌曲，更新状态
            val currentIndex = _playerState.value.currentIndex
            if (index == currentIndex && currentPlaylist.isNotEmpty()) {
                if (currentIndex < currentPlaylist.size) {
                    controller?.seekToDefaultPosition(currentIndex)
                } else {
                    controller?.seekToDefaultPosition(currentPlaylist.size - 1)
                }
            }
            // 更新 PlayerState
            updatePlayerState()
        }
    }

    override fun clearQueue() {
        currentPlaylist = emptyList()
        controller?.setMediaItems(emptyList())
        // 更新 PlayerState
        updatePlayerState()
    }

    override fun seekToQueueItem(index: Int) {
        if (index >= 0 && index < currentPlaylist.size) {
            controller?.seekToDefaultPosition(index)
            updatePlayerState()
        }
    }

    private fun processUrl(url: String?): String? {
        return url?.let {
            if (it.startsWith("http")) it else "http://39.106.30.151:9000$it"
        }
    }

    private fun Music.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(processUrl(url))
            .setCustomCacheKey("cache_${id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDescription(description)
                    .setArtist(artists.joinToString(", ") { it.name })
                    .setAlbumTitle(album?.title)
                    .setExtras(
                        bundleOf(
                            "LYRICS" to lyrics,
                            "LYRICS_TYPE" to "LRC"
                        )
                    )
                    .apply {
                        coverImage?.let { setArtworkUri(processUrl(it)?.toUri()) }
                    }
                    .build()
            )
            .build()
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.RANDOM
    }
}
