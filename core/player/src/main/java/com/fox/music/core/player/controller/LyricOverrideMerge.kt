package com.fox.music.core.player.controller

import com.fox.music.core.model.music.LyricOverride
import com.fox.music.core.model.music.Music

/**
 * 列表/分页/再次点歌带回来的 [incoming] 经常仍是曲库旧歌词。
 * 有本会话覆盖时固定用覆盖；没有时保持原规则：incoming 非空优先，空则留 previous。
 */
fun mergeMusicKeepingLyricOverride(
    incoming: Music,
    previous: Music?,
    override: LyricOverride?,
): Music {
    val lyricsUrl = incoming.lyricsUrl?.takeIf { it.isNotBlank() } ?: previous?.lyricsUrl
    if (override != null && override.lyrics.isNotBlank()) {
        if (
            incoming.lyrics == override.lyrics &&
            incoming.lyricsTrans == override.lyricsTrans &&
            incoming.lyricsUrl == lyricsUrl
        ) {
            return incoming
        }
        return incoming.copy(
            lyrics = override.lyrics,
            lyricsTrans = override.lyricsTrans,
            lyricsUrl = lyricsUrl,
        )
    }
    if (previous == null) {
        return if (lyricsUrl == incoming.lyricsUrl) incoming else incoming.copy(lyricsUrl = lyricsUrl)
    }
    val lyrics = incoming.lyrics?.takeIf { it.isNotBlank() } ?: previous.lyrics
    val lyricsTrans = incoming.lyricsTrans?.takeIf { it.isNotBlank() } ?: previous.lyricsTrans
    if (lyrics == incoming.lyrics && lyricsTrans == incoming.lyricsTrans && lyricsUrl == incoming.lyricsUrl) {
        return incoming
    }
    return incoming.copy(
        lyrics = lyrics,
        lyricsTrans = lyricsTrans,
        lyricsUrl = lyricsUrl,
    )
}
