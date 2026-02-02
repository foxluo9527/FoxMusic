package com.fox.music.core.player

import com.fox.music.core.model.Music
import com.fox.music.core.model.PlayerState
import com.fox.music.core.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

interface MusicController {

    val playerState: StateFlow<PlayerState>

    val currentPosition: StateFlow<Long>

    fun play()

    fun pause()

    fun stop()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun setPlaylist(musics: List<Music>, startIndex: Int = 0)

    fun addToQueue(music: Music)

    fun setRepeatMode(repeatMode: RepeatMode)

    fun toggleShuffle()

    fun setPlaybackSpeed(speed: Float)
}
