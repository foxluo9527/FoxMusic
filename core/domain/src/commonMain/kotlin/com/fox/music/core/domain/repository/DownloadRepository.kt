package com.fox.music.core.domain.repository

import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.model.music.Music
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    val downloads: Flow<List<DownloadTask>>

    /** 已完成下载的歌曲数量，供个人中心等入口展示。 */
    val completedCount: Flow<Int>

    suspend fun enqueue(musics: List<Music>)

    suspend fun pause(musicId: Long)

    suspend fun resume(musicId: Long)

    suspend fun cancel(musicId: Long)

    suspend fun delete(musicId: Long)

    suspend fun pauseAll()

    suspend fun resumeAll()

    suspend fun getTotalDownloadedBytes(): Long

    /**
     * 若该歌曲已下载完成且本地文件仍存在，返回可用于播放的 `file://` URL；否则返回 null。
     */
    suspend fun getLocalPlayUrl(musicId: Long): String?
}
