package com.fox.music.core.model.music

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
)
