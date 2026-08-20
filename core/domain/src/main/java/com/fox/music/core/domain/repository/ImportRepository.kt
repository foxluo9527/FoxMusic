package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result

interface ImportRepository {

    suspend fun importMusic(url: String, platform: String? = "mobile"): Result<ImportMusicResponse>

    suspend fun importNeteaseCharts(platform: String? = "mobile"): Result<ImportChartsResult>

    /** 启动热门歌单同步任务，成功时返回服务端 message */
    suspend fun importHotPlaylists(platform: String? = "mobile"): Result<String>
}

data class ImportMusicResponse(
    val albumId: Long? = null,
    val taskId: String? = null,
    val isImporting: Boolean = false,
)

data class ImportChartsResult(
    val total: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
)
