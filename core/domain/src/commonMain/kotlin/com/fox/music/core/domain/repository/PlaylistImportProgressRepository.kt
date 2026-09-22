package com.fox.music.core.domain.repository

import kotlinx.coroutines.flow.Flow

data class PlaylistImportProgress(
    val jobId: String,
    val playlistId: Long,
    val status: String,
    val total: Int,
    val done: Int,
    val failed: Int,
    /** 当前正在处理的曲目名（曲库复用很快时可能为空） */
    val currentTitle: String? = null,
)

interface PlaylistImportProgressRepository {
    fun observe(playlistId: Long): Flow<PlaylistImportProgress?>
    suspend fun pause(jobId: String)
    suspend fun resume(jobId: String)
    suspend fun retryFailed(jobId: String)
    suspend fun cancelFailed(jobId: String)
}
