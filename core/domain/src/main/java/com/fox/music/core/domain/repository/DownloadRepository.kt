package com.fox.music.core.domain.repository

import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.model.music.Music
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    val downloads: Flow<List<DownloadTask>>

    suspend fun enqueue(musics: List<Music>)

    suspend fun pause(musicId: Long)

    suspend fun resume(musicId: Long)

    suspend fun cancel(musicId: Long)

    suspend fun delete(musicId: Long)

    suspend fun pauseAll()

    suspend fun resumeAll()

    suspend fun getTotalDownloadedBytes(): Long
}
