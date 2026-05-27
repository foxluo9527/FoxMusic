package com.fox.music.core.model.music

data class PlaybackSnapshot(
    val playingKey: String? = null,
    val playlist: List<Music> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.ALL,
) {
    val isValid: Boolean
        get() = playlist.isNotEmpty() && currentIndex in playlist.indices
}
