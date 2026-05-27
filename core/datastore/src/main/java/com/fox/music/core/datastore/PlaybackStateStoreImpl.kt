package com.fox.music.core.datastore

import com.fox.music.core.common.player.PlaybackStateStore
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackSnapshot
import com.fox.music.core.model.music.RepeatMode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateStoreImpl @Inject constructor(
    private val preferencesDataStore: FoxPreferencesDataStore,
) : PlaybackStateStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun loadSnapshot(): PlaybackSnapshot? {
        val raw = preferencesDataStore.getPlaybackSnapshotJson() ?: return null
        return runCatching {
            val playlist = json.decodeFromString(ListSerializer(Music.serializer()), raw)
            val index = preferencesDataStore.getPlaybackCurrentIndex()
            val position = preferencesDataStore.getPlaybackPositionMs()
            val key = preferencesDataStore.getPlaybackPlayingKey()
            val mode = loadRepeatMode()
            PlaybackSnapshot(
                playingKey = key,
                playlist = playlist,
                currentIndex = index,
                positionMs = position,
                repeatMode = mode,
            ).takeIf { it.isValid }
        }.getOrNull()
    }

    override suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
        if (snapshot.playlist.isEmpty()) {
            preferencesDataStore.clearPlaybackSnapshot()
            return
        }
        val capped = snapshot.copy(
            playlist = snapshot.playlist.take(MAX_PLAYLIST_SIZE),
            currentIndex = snapshot.currentIndex.coerceIn(0, snapshot.playlist.lastIndex),
        )
        preferencesDataStore.savePlaybackSnapshotJson(
            json.encodeToString(ListSerializer(Music.serializer()), capped.playlist)
        )
        preferencesDataStore.savePlaybackCurrentIndex(capped.currentIndex)
        preferencesDataStore.savePlaybackPositionMs(capped.positionMs)
        preferencesDataStore.savePlaybackPlayingKey(capped.playingKey)
        saveRepeatMode(capped.repeatMode)
    }

    override suspend fun loadRepeatMode(): RepeatMode {
        val name = preferencesDataStore.getRepeatModeName()
        return runCatching { RepeatMode.valueOf(name) }.getOrDefault(RepeatMode.ALL)
    }

    override suspend fun saveRepeatMode(mode: RepeatMode) {
        preferencesDataStore.saveRepeatModeName(mode.name)
    }

    companion object {
        private const val MAX_PLAYLIST_SIZE = 500
    }
}
