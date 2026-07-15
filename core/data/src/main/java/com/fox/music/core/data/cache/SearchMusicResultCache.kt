package com.fox.music.core.data.cache

import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单曲搜索分页内存缓存（平台曲库 / QQ / 网易云）。
 * 同一关键词在多平台间切换时优先命中缓存，减轻服务端压力。
 */
@Singleton
class SearchMusicResultCache @Inject constructor() {

    private data class CacheKey(
        val keyword: String,
        val platform: String,
        val page: Int,
        val limit: Int,
    )

    private data class CacheEntry(
        val data: PagedData<Music>,
        val expiresAtMs: Long,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<CacheKey, CacheEntry>(MAX_ENTRIES, 0.75f, true)

    fun get(
        keyword: String,
        platform: String?,
        page: Int,
        limit: Int,
    ): PagedData<Music>? {
        val key = CacheKey(
            keyword = normalizeKeyword(keyword),
            platform = normalizePlatform(platform),
            page = page,
            limit = limit,
        )
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = entries[key] ?: return null
            if (entry.expiresAtMs < now) {
                entries.remove(key)
                return null
            }
            return entry.data
        }
    }

    fun put(
        keyword: String,
        platform: String?,
        page: Int,
        limit: Int,
        data: PagedData<Music>,
    ) {
        val key = CacheKey(
            keyword = normalizeKeyword(keyword),
            platform = normalizePlatform(platform),
            page = page,
            limit = limit,
        )
        val entry = CacheEntry(
            data = data,
            expiresAtMs = System.currentTimeMillis() + TTL_MS,
        )
        synchronized(lock) {
            entries[key] = entry
            while (entries.size > MAX_ENTRIES) {
                val eldest = entries.entries.iterator()
                if (eldest.hasNext()) {
                    eldest.next()
                    eldest.remove()
                } else {
                    break
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    private fun normalizeKeyword(keyword: String): String = keyword.trim().lowercase()

    private fun normalizePlatform(platform: String?): String =
        platform?.trim()?.lowercase().orEmpty().ifEmpty { PLATFORM_FOX }

    companion object {
        private const val PLATFORM_FOX = "fox"
        private const val MAX_ENTRIES = 80
        private const val TTL_MS = 10 * 60 * 1000L // 10 分钟
    }
}
