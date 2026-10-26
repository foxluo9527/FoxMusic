package com.fox.music.core.data.cache

import android.content.Context
import com.fox.music.core.domain.repository.AllArtistsCache
import com.fox.music.core.model.music.Artist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「全部歌手」全量列表的本地快照。
 *
 * 为什么用文件而不是 DataStore：5000+ 位歌手序列化后 1MB 量级，Preferences DataStore
 * 是为小数据设计的，塞这么大的 blob 不合算；cacheDir 里的普通文件读写既够快也够简单。
 *
 * 用途：进页面先渲染这份快照（秒开），网络回来后再替换 —— 用户看到的是「列表直接在那儿，
 * 有变化才动」，而不是转圈等待。
 */
@Singleton
class AllArtistsCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AllArtistsCache {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File
        get() = File(context.cacheDir, FILE_NAME)

    override suspend fun read(): List<Artist>? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file
            if (!f.exists()) return@withContext null
            json.decodeFromString<List<Artist>>(f.readText())
        }.getOrNull()
    }

    override suspend fun write(artists: List<Artist>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val f = file
                val tmp = File(f.parentFile, "${f.name}.tmp")
                tmp.writeText(json.encodeToString(artists))
                // 先写临时文件再 rename：写一半被杀也不会留下半截 JSON
                if (tmp.renameTo(f)) Unit else tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        }
    }

    /** 快照写入时间（毫秒），用于判断要不要重新回源刷新 */
    override suspend fun lastWriteAt(): Long = withContext(Dispatchers.IO) {
        runCatching { file.lastModified() }.getOrDefault(0L)
    }

    private companion object {
        // 改序列化形状时升版本，旧文件自然失效
        const val FILE_NAME = "all_artists_cache_v1.json"
    }
}
