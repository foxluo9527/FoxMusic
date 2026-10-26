package com.fox.music.core.model.music

import kotlinx.serialization.Serializable

@Serializable
data class ListeningStats(
    val days: Int = 7,
    val totalDurationSec: Long = 0,
    val playCount: Int = 0,
    val uniqueTracks: Int = 0,
    val topTracks: List<Music> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
) {
    /** 向上取整到分钟，避免听了不到 60 秒也显示 0 */
    val totalDurationMinutes: Long
        get() = when {
            totalDurationSec <= 0L -> 0L
            totalDurationSec < 60L -> 1L
            else -> totalDurationSec / 60
        }
}
