package com.fox.music.widget

import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode

data class WidgetPlaybackState(
    val title: String = "FoxMusic",
    val artist: String = "暂无播放",
    val coverUrl: String? = null,
    val coverLocalPath: String? = null,
    val isPlaying: Boolean = false,
    val currentLyric: String = "暂无歌词",
    val lyricsRaw: String? = null,
    val positionMs: Long = 0L,
    val largeLyricLines: List<String> = emptyList(),
    val largeLyricCurrentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isFavorite: Boolean = false,
    val musicId: Long? = null,
)

object WidgetStateKeys {
    const val PREFS_NAME = "fox_music_widget"
    const val TITLE = "widget_title"
    const val ARTIST = "widget_artist"
    const val COVER_URL = "widget_cover_url"
    const val COVER_LOCAL_PATH = "widget_cover_local_path"
    const val IS_PLAYING = "widget_is_playing"
    const val CURRENT_LYRIC = "widget_current_lyric"
    const val LYRICS_RAW = "widget_lyrics_raw"
    const val POSITION_MS = "widget_position_ms"
    const val REPEAT_MODE = "widget_repeat_mode"
    const val IS_FAVORITE = "widget_is_favorite"
    const val MUSIC_ID = "widget_music_id"
    const val LYRIC_LINE_PREFIX = "widget_lyric_line_"
    const val LYRIC_CURRENT_INDEX = "widget_lyric_current_index"
    const val DARK_MODE = "widget_dark_mode"
    const val APP_THEME_ID = "widget_app_theme_id"
    const val CUSTOM_THEME_SEED = "widget_custom_theme_seed"
    const val DARK_THEME = "widget_dark_theme"
}

object WidgetLyricHelper {
    private const val LARGE_LYRIC_LINE_COUNT = 5

    fun buildSnapshot(state: PlayerState, positionMs: Long): WidgetPlaybackState? {
        val music = state.currentMusic ?: return null
        val artist = music.artists.joinToString { it.name }.ifBlank { "未知歌手" }
        val lyricWindow = resolveLargeLyricWindow(music.lyrics, positionMs)
        return WidgetPlaybackState(
            title = music.title,
            artist = artist,
            coverUrl = music.coverImage,
            isPlaying = state.isPlaying,
            currentLyric = music.getCurrentLyric(positionMs)?.takeIf { it.isNotBlank() } ?: "暂无歌词",
            lyricsRaw = music.lyrics,
            positionMs = positionMs.coerceAtLeast(0L),
            largeLyricLines = lyricWindow.lines,
            largeLyricCurrentIndex = lyricWindow.currentLineIndex,
            repeatMode = state.repeatMode,
            isFavorite = state.isFavorite || music.isFavorite,
            musicId = music.id,
        )
    }

    fun titleMeta(title: String, artist: String): String =
        "$title - $artist"

    fun largeLyricLines(music: Music, positionMs: Long): List<String> =
        resolveLargeLyricWindow(music.lyrics, positionMs).lines

    fun largeLyricWindow(music: Music, positionMs: Long): LargeLyricWindow =
        resolveLargeLyricWindow(music.lyrics, positionMs)

    fun resolveLargeLyricWindow(state: WidgetPlaybackState): LargeLyricWindow {
        if (!state.lyricsRaw.isNullOrBlank()) {
            return resolveLargeLyricWindow(state.lyricsRaw, state.positionMs)
        }
        val cachedLines = state.largeLyricLines.filter { it.isNotBlank() }
        if (cachedLines.isNotEmpty()) {
            return LargeLyricWindow(
                lines = state.largeLyricLines,
                currentLineIndex = state.largeLyricCurrentIndex.coerceAtLeast(0),
            )
        }
        return LargeLyricWindow(
            lines = listOf(state.currentLyric.ifBlank { "暂无歌词" }),
            currentLineIndex = 0,
        )
    }

    fun resolveLargeLyricWindow(lyricsRaw: String?, positionMs: Long): LargeLyricWindow {
        val lyricLines = LyricsParser.parseLrc(lyricsRaw.orEmpty())
        return buildLargeLyricWindow(lyricLines, positionMs)
    }

    data class LargeLyricWindow(
        val lines: List<String>,
        val currentLineIndex: Int,
    )

    private fun buildLargeLyricWindow(
        lyricLines: List<LyricsParser.LyricLine>,
        positionMs: Long,
    ): LargeLyricWindow {
        if (lyricLines.isEmpty()) {
            return LargeLyricWindow(
                lines = listOf("暂无歌词"),
                currentLineIndex = 0,
            )
        }
        val lines = lyricLines.map { it.text }
        val currentIndex = lyricLines
            .indexOfLast { it.startTimeMs <= positionMs }
            .coerceAtLeast(0)
        val start = (currentIndex - 2).coerceAtLeast(0)
        val windowLines = List(LARGE_LYRIC_LINE_COUNT) { index ->
            lines.getOrNull(start + index).orEmpty()
        }
        return LargeLyricWindow(
            lines = windowLines,
            currentLineIndex = currentIndex - start,
        )
    }
}
