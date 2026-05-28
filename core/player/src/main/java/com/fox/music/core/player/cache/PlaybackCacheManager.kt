package com.fox.music.core.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.repository.PlaybackCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlaybackCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: FoxPreferencesDataStore,
) : PlaybackCacheRepository {

    @Volatile
    private var simpleCache: SimpleCache? = null

    override val cacheMaxBytes: Flow<Long> = dataStore.cacheMaxBytes

    fun attachCache(cache: SimpleCache) {
        simpleCache = cache
    }

    fun detachCache() {
        simpleCache = null
    }

    private fun cacheDirectory(): File = File(context.cacheDir, CACHE_DIR_NAME)

    override suspend fun getCacheUsedBytes(): Long = withContext(Dispatchers.IO) {
        directorySize(cacheDirectory())
    }

    override suspend fun clearCache(): Result<Unit> = suspendRunCatching {
        withContext(Dispatchers.IO) {
            val cache = simpleCache
            if (cache != null) {
                cache.keys.toList().forEach { key ->
                    cache.removeResource(key)
                }
            } else {
                cacheDirectory().deleteRecursively()
            }
        }
        Unit
    }

    override suspend fun updateCacheMaxBytes(maxBytes: Long): Result<Unit> = suspendRunCatching {
        dataStore.updateCacheMaxBytes(maxBytes)
        Unit
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        const val CACHE_DIR_NAME = "music_cache"
    }
}
