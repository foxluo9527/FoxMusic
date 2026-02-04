package com.fox.music.core.player.controller

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

    /**
     * 无感切换歌单列表
     */
    fun updatePlaylist(musics: List<Music>,key: String)

    fun setPlaylist(musics: List<Music>, startIndex: Int = 0,key: String)

    fun addToQueue(music: Music)

    fun setRepeatMode(repeatMode: RepeatMode)

    fun setPlaybackSpeed(speed: Float)
}
