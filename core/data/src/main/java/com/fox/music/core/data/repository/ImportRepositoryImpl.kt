package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.domain.repository.ImportMusicResponse
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.network.api.ImportApiService
import com.fox.music.core.network.model.ImportMusicRequest
import javax.inject.Inject

class ImportRepositoryImpl @Inject constructor(
    private val importApiService: ImportApiService
) : ImportRepository {

    override suspend fun importMusic(url: String, platform: String?): Result<ImportMusicResponse> {
        return suspendRunCatching {
            val response = importApiService.importMusic(
                ImportMusicRequest(url = url, platform = platform)
            )
            val data = response.data
            ImportMusicResponse(
                albumId = data?.albumId,
                taskId = data?.taskId,
                isImporting = data?.isImporting ?: false
            )
        }
    }
}
