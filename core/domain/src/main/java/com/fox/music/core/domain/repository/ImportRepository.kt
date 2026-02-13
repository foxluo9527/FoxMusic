package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result

interface ImportRepository {

    suspend fun importMusic(url: String, platform: String? = "mobile"): Result<ImportMusicResponse>
}

data class ImportMusicResponse(
    val albumId: Long? = null,
    val taskId: String? = null,
    val isImporting: Boolean = false,
)
