package com.fox.music.core.player.lyric

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.controller.MusicController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Singleton
class CarBluetoothLyricMetadataCoordinator @Inject constructor(
    private val musicController: MusicController,
    private val preferencesDataStore: FoxPreferencesDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: Player? = null
    private var bluetoothLyricEnabled = false
    private var lastPublishedKey: String? = null

    fun attach(player: Player) {
        this.player?.removeListener(listener)
        this.player = player
        player.addListener(listener)
        scope.launch { observeMetadataUpdates() }
    }

    fun detach() {
        player?.removeListener(listener)
        player = null
        scope.cancel()
        lastPublishedKey = null
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            lastPublishedKey = null
            publishMetadata()
        }
    }

    private suspend fun observeMetadataUpdates() {
        combine(
            musicController.playerState,
            musicController.currentPosition,
            preferencesDataStore.carBluetoothLyricEnabled,
        ) { state, position, enabled ->
            MetadataSnapshot(
                music = state.currentMusic,
                positionMs = position,
                enabled = enabled,
                lyricIndex = lyricIndex(state.currentMusic, position, enabled),
            )
        }
            .distinctUntilChanged()
            .collect { snapshot ->
                bluetoothLyricEnabled = snapshot.enabled
                publishMetadata(snapshot)
            }
    }

    private fun publishMetadata(snapshot: MetadataSnapshot? = null) {
        val player = player ?: return
        val currentSnapshot = snapshot ?: MetadataSnapshot(
            music = musicController.playerState.value.currentMusic,
            positionMs = musicController.currentPosition.value,
            enabled = bluetoothLyricEnabled,
            lyricIndex = lyricIndex(
                musicController.playerState.value.currentMusic,
                musicController.currentPosition.value,
                bluetoothLyricEnabled,
            ),
        )
        val music = currentSnapshot.music ?: run {
            lastPublishedKey = null
            return
        }
        if (player.mediaItemCount == 0) return

        val index = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
        val mediaItem = player.getMediaItemAt(index)
        val metadata = CarBluetoothLyricMetadata.build(
            music = music,
            positionMs = currentSnapshot.positionMs,
            enabled = currentSnapshot.enabled,
            base = mediaItem.mediaMetadata,
        )
        val publishKey = buildPublishKey(currentSnapshot, metadata.title?.toString(), metadata.artist?.toString())
        if (publishKey == lastPublishedKey) return
        lastPublishedKey = publishKey

        player.replaceMediaItem(
            index,
            mediaItem.buildUpon().setMediaMetadata(metadata).build(),
        )
    }

    private fun buildPublishKey(snapshot: MetadataSnapshot, title: String?, artist: String?): String =
        "${snapshot.music?.id}|${snapshot.enabled}|$title|$artist|${snapshot.lyricIndex}"

    private fun lyricIndex(music: Music?, positionMs: Long, enabled: Boolean): Int {
        if (!enabled) return -1
        val lines = music?.lyricLines.orEmpty()
        if (lines.isEmpty()) return -1
        return lines.indexOfLast { it.startTimeMs <= positionMs }.coerceAtLeast(0)
    }

    private data class MetadataSnapshot(
        val music: Music?,
        val positionMs: Long,
        val enabled: Boolean,
        val lyricIndex: Int,
    )
}
