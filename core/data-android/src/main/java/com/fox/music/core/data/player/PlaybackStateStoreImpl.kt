package com.fox.music.core.data.player

import com.fox.music.core.common.player.PlaybackStateStore
import com.fox.music.core.database.dao.PlaybackQueueDao
import com.fox.music.core.database.entity.PlaybackQueueEntity
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.model.music.LyricOverride
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSnapshot
import com.fox.music.core.model.music.RepeatMode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放队列落 Room（含完整歌词），索引/进度等标量仍在 DataStore。
 * 首次启动若 Room 为空且 DataStore 仍有旧 JSON 快照，则一次性迁移。
 */
@Singleton
class PlaybackStateStoreImpl @Inject constructor(
    private val preferencesDataStore: FoxPreferencesDataStore,
    private val playbackQueueDao: PlaybackQueueDao,
) : PlaybackStateStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var lastSavedQueueSignature: String? = null

    @Volatile
    private var lastLyricOverridesJson: String? = null

    private val lyricOverridesSerializer =
        MapSerializer(serializer<Long>(), LyricOverride.serializer())

    override suspend fun loadSnapshot(): PlaybackSnapshot? {
        var playlist = loadPlaylistFromDb()
        if (playlist.isEmpty()) {
            playlist = migrateLegacyDataStoreSnapshot()
        }
        if (playlist.isEmpty()) return null

        val index = preferencesDataStore.getPlaybackCurrentIndex()
            .coerceIn(0, playlist.lastIndex)
        val position = preferencesDataStore.getPlaybackPositionMs()
        val key = preferencesDataStore.getPlaybackPlayingKey()
        val mode = loadRepeatMode()
        val source = preferencesDataStore.getPlaybackPlaylistSourceJson()?.let { raw ->
            runCatching {
                json.decodeFromString(PlaybackPlaylistSource.serializer(), raw)
            }.getOrNull()
        }
        return PlaybackSnapshot(
            playingKey = key,
            playlist = playlist,
            currentIndex = index,
            positionMs = position,
            repeatMode = mode,
            playlistSource = source,
            lyricOverrides = loadLyricOverrides(),
        ).takeIf { it.isValid }?.also {
            lastSavedQueueSignature = queueSignature(it.playlist)
        }
    }

    override suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
        if (snapshot.playlist.isEmpty()) {
            playbackQueueDao.clear()
            preferencesDataStore.clearPlaybackSnapshot()
            lastSavedQueueSignature = null
            lastLyricOverridesJson = ""
            return
        }
        val cappedPlaylist = snapshot.playlist.take(MAX_PLAYLIST_SIZE)
        val capped = snapshot.copy(
            playlist = cappedPlaylist,
            currentIndex = snapshot.currentIndex.coerceIn(0, cappedPlaylist.lastIndex),
        )
        val signature = queueSignature(capped.playlist)
        if (signature != lastSavedQueueSignature) {
            val entities = capped.playlist.mapIndexed { index, music ->
                PlaybackQueueEntity(
                    queueIndex = index,
                    musicId = music.id,
                    musicJson = json.encodeToString(Music.serializer(), music),
                )
            }
            playbackQueueDao.replaceAll(entities)
            lastSavedQueueSignature = signature
        }
        // 清掉旧 DataStore 大 blob（迁移后不再写入）
        preferencesDataStore.clearPlaybackSnapshotPlaylistJson()
        preferencesDataStore.savePlaybackCurrentIndex(capped.currentIndex)
        preferencesDataStore.savePlaybackPositionMs(capped.positionMs)
        preferencesDataStore.savePlaybackPlayingKey(capped.playingKey)
        preferencesDataStore.savePlaybackPlaylistSourceJson(
            capped.playlistSource?.let { json.encodeToString(PlaybackPlaylistSource.serializer(), it) },
        )
        saveRepeatMode(capped.repeatMode)
        saveLyricOverrides(capped.lyricOverrides)
    }

    override suspend fun loadRepeatMode(): RepeatMode {
        val name = preferencesDataStore.getRepeatModeName()
        return runCatching { RepeatMode.valueOf(name) }.getOrDefault(RepeatMode.ALL)
    }

    override suspend fun saveRepeatMode(mode: RepeatMode) {
        preferencesDataStore.saveRepeatModeName(mode.name)
    }

    private suspend fun loadPlaylistFromDb(): List<Music> {
        return playbackQueueDao.getAllOrdered().mapNotNull { entity ->
            runCatching {
                json.decodeFromString(Music.serializer(), entity.musicJson)
            }.getOrNull()
        }
    }

    /** 将旧版 DataStore JSON 队列迁入 Room，并删除 preferences 中的大字段。 */
    private suspend fun migrateLegacyDataStoreSnapshot(): List<Music> {
        val raw = preferencesDataStore.getPlaybackSnapshotJson() ?: return emptyList()
        val playlist = runCatching {
            json.decodeFromString(ListSerializer(Music.serializer()), raw)
        }.getOrElse { emptyList() }
            .take(MAX_PLAYLIST_SIZE)
        if (playlist.isEmpty()) {
            preferencesDataStore.clearPlaybackSnapshotPlaylistJson()
            return emptyList()
        }
        val entities = playlist.mapIndexed { index, music ->
            PlaybackQueueEntity(
                queueIndex = index,
                musicId = music.id,
                musicJson = json.encodeToString(Music.serializer(), music),
            )
        }
        playbackQueueDao.replaceAll(entities)
        preferencesDataStore.clearPlaybackSnapshotPlaylistJson()
        val rawIndex = preferencesDataStore.getPlaybackCurrentIndex()
        val savedIndex = rawIndex.coerceIn(0, playlist.lastIndex)
        if (savedIndex != rawIndex) {
            preferencesDataStore.savePlaybackCurrentIndex(savedIndex)
        }
        lastSavedQueueSignature = queueSignature(playlist)
        return playlist
    }

    private suspend fun loadLyricOverrides(): Map<Long, LyricOverride> {
        val raw = preferencesDataStore.getPlaybackLyricOverridesJson()?.takeIf { it.isNotBlank() }
            ?: run {
                lastLyricOverridesJson = ""
                return emptyMap()
            }
        lastLyricOverridesJson = raw
        return runCatching {
            json.decodeFromString(lyricOverridesSerializer, raw)
        }.getOrDefault(emptyMap())
    }

    private suspend fun saveLyricOverrides(overrides: Map<Long, LyricOverride>) {
        val encoded = if (overrides.isEmpty()) {
            ""
        } else {
            json.encodeToString(lyricOverridesSerializer, overrides)
        }
        if (encoded == lastLyricOverridesJson) return
        preferencesDataStore.savePlaybackLyricOverridesJson(encoded.takeIf { it.isNotEmpty() })
        lastLyricOverridesJson = encoded
    }

    private fun queueSignature(playlist: List<Music>): String =
        playlist.joinToString(separator = "|") { music ->
            buildString {
                append(music.id)
                append(':')
                append(music.url.hashCode())
                // 歌词正文而不只是长度：同长度替换也要写回 Room，否则重进会读到旧歌词
                append(':')
                append(music.lyrics?.hashCode() ?: 0)
                append(':')
                append(music.lyricsTrans?.hashCode() ?: 0)
                append(':')
                append(if (music.isFavorite) 1 else 0)
            }
        }

    companion object {
        private const val MAX_PLAYLIST_SIZE = 500
    }
}
