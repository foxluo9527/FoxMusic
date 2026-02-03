package com.fox.music.core.model

data class PlayerState(
    val currentMusic: Music? = null,
    val playlist: List<Music> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val shuffleMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isFavorite: Boolean = false
)

enum class RepeatMode {
    RANDOM,
    ONE,
    ALL
}

enum class PlayerEvent {
    PLAY,
    PAUSE,
    STOP,
    NEXT,
    PREVIOUS,
    SEEK,
    COMPLETE
}
