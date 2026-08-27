package com.fox.music.core.data.cache

import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.domain.repository.RecommendedMusicCacheManager
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.RecommendedMusicCache
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RecommendedMusicCacheManagerImpl @Inject constructor(
    private val musicRepository: MusicRepository,
    private val cacheStore: RecommendedMusicCacheStore,
) : RecommendedMusicCacheManager {

    private val prefetchMutex = Mutex()
    private var memoryCache: RecommendedMusicCache? = null
    private var loadedFromDisk = false

    override suspend fun getCache(): RecommendedMusicCache? {
        ensureLoadedFromDisk()
        val cache = memoryCache ?: return null
        return cache.takeUnless { isExpired(cache) }
    }

    override fun isExpired(cache: RecommendedMusicCache): Boolean {
        return cache.cacheDate != today()
    }

    override suspend fun prefetchIfNeeded(force: Boolean) {
        ensureLoadedFromDisk()
        if (!force) {
            memoryCache?.takeIf { !isExpired(it) }?.let { return }
        }

        prefetchMutex.withLock {
            if (!force) {
                memoryCache?.takeIf { !isExpired(it) }?.let { return }
            }

            runCatching { fetchAll() }
                .onSuccess { cache ->
                    memoryCache = cache
                    cacheStore.save(cache)
                }
        }
    }

    override suspend fun clear() {
        memoryCache = null
        loadedFromDisk = false
        cacheStore.clear()
    }

    private suspend fun ensureLoadedFromDisk() {
        if (loadedFromDisk) return
        memoryCache = cacheStore.load()
        loadedFromDisk = true
    }

    private suspend fun fetchAll(): RecommendedMusicCache {
        var seed: String? = null
        val all = mutableListOf<Music>()
        var page = 1
        while (all.size < MAX_ITEMS) {
            val result = musicRepository.getMusicList(
                page = page,
                limit = PAGE_LIMIT,
                sort = SORT_RECOMMEND,
                seed = seed,
            ).getOrThrow()
            if (page == 1) {
                seed = result.recommendSeed.orEmpty()
            }
            all += result.list
            if (!result.hasMore || result.list.isEmpty()) break
            page++
        }
        return RecommendedMusicCache(
            seed = seed.orEmpty(),
            cacheDate = today(),
            items = all.take(MAX_ITEMS),
        )
    }

    private fun today(): String {
        return LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMATTER)
    }

    companion object {
        const val MAX_ITEMS = 100
        private const val PAGE_LIMIT = 20
        private const val SORT_RECOMMEND = "recommend"
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
