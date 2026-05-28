package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import kotlinx.coroutines.flow.Flow

interface PlaybackCacheRepository {

    val cacheMaxBytes: Flow<Long>

    suspend fun getCacheUsedBytes(): Long

    suspend fun clearCache(): Result<Unit>

    suspend fun updateCacheMaxBytes(maxBytes: Long): Result<Unit>
}
