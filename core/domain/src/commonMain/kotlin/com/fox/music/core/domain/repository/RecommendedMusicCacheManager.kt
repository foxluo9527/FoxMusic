package com.fox.music.core.domain.repository

import com.fox.music.core.model.music.RecommendedMusicCache

interface RecommendedMusicCacheManager {
    suspend fun getCache(): RecommendedMusicCache?
    fun isExpired(cache: RecommendedMusicCache): Boolean
    suspend fun prefetchIfNeeded(force: Boolean = false)
    suspend fun clear()
}
