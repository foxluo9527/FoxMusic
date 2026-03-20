package com.fox.music.core.player.controller

import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import kotlinx.coroutines.flow.StateFlow

interface MusicController {

    val playerState: StateFlow<PlayerState>

    val currentPosition: StateFlow<Long>

    fun play()

    fun pause()

    fun togglePlay()

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

    /**
     * 从播放队列中删除指定索引的歌曲
     */
    fun removeFromQueue(index: Int)

    /**
     * 清空播放队列
     */
    fun clearQueue()

    /**
     * 跳转到指定索引的歌曲进行播放
     */
    fun seekToQueueItem(index: Int)
}
