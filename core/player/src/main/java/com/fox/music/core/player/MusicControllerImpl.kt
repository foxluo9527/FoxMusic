package com.fox.music.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fox.music.core.model.Music
import com.fox.music.core.model.PlayerState
import com.fox.music.core.model.RepeatMode
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var currentPlaylist: List<Music> = emptyList()

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync().also { future ->
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
                updatePlayerState()
                if (playbackState == Player.STATE_ENDED) {
                    updatePlayerState()
                }
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
                        duration = player.duration.coerceAtLeast(0)
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

    override fun stop() {
        controller?.stop()
    }

    override fun next() {
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun setPlaylist(musics: List<Music>, startIndex: Int) {
        currentPlaylist = musics
        val mediaItems = musics.map { it.toMediaItem() }
        controller?.run {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
            play()
        }
    }

    override fun addToQueue(music: Music) {
        currentPlaylist = currentPlaylist + music
        controller?.addMediaItem(music.toMediaItem())
    }

    override fun setRepeatMode(repeatMode: RepeatMode) {
        controller?.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
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
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDescription(description)
                    .setArtist(artists.joinToString(", ") { it.name })
                    .setAlbumTitle(album?.title)
                    .apply {
                        coverImage?.let { setArtworkUri(Uri.parse(it)) }
                    }
                    .build()
            )
            .build()
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
}
