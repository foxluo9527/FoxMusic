package com.fox.music.iosshared

import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PlayHistoryDto
import com.fox.music.core.network.model.PlaylistDetailDto
import com.fox.music.core.network.model.PlaylistDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * 首页 / 发现 / 社区「第一页数据」磁盘缓存。
 *
 * 为什么做：这三个 Tab 冷启动后都要等接口回来才出数据（骨架/空白十几秒）。
 * 这里把每个 Tab 的**第一页**接口结果落盘，冷启动先用上次快照秒出，之后接口拉到
 * 新数据再覆盖刷新 —— 即 stale-while-revalidate。
 *
 * 约定（与本地持久化必须带账号维度的铁律一致）：
 * - key 必须带 ownerUserId（未登录走 "anon"），避免 A 账号缓存被 B 账号读到。
 * - 只缓存 DTO（core/network 里都是 @Serializable），不依赖 domain 模型是否可序列化。
 * - 任何读写失败都静默吞掉，绝不影响正常网络加载。
 */
object FoxFirstPageCache {

    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 当前账号维度 key 前缀；未登录用 anon。 */
    fun ownerPrefix(): String = FoxAuthStore.user?.id?.toString() ?: "anon"

    @OptIn(ExperimentalForeignApi::class)
    internal fun dir(): String? {
        val base = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String) ?: return null
        val d = "$base/fox_firstpage_v1"
        NSFileManager.defaultManager.createDirectoryAtPath(d, true, null, null)
        return d
    }

    internal fun pathForKey(key: String): String? {
        val safe = key.replace(Regex("[^A-Za-z0-9_]+"), "_")
        return dir()?.let { "$it/$safe.json" }
    }

    // 真正的 cinterop 文件 I/O 是非 inline 私有函数（自带 @OptIn），
    // 这样 inline 的 load/save 只做 JSON 编解码、内联体里不含 cinterop，
    // 调用方（首页/发现/社区屏幕）无需再声明 ExperimentalForeignApi。
    @OptIn(ExperimentalForeignApi::class)
    internal fun readBytesFromPath(path: String): ByteArray? = runCatching {
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
        data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
    }.getOrNull()

    @OptIn(ExperimentalForeignApi::class)
    internal fun writeBytesToPath(path: String, bytes: ByteArray): Unit = runCatching {
        val f = fopen(path, "wb") ?: return
        try {
            bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f) }
        } finally {
            fclose(f)
        }
    }.let { }

    internal suspend fun <T> load(key: String, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T? =
        withContext(Dispatchers.Default) {
            runCatching {
                val p = pathForKey(key) ?: return@runCatching null
                val bytes = readBytesFromPath(p) ?: return@runCatching null
                json.decodeFromString(deserializer, bytes.decodeToString())
            }.onFailure {
                println("FoxFirstPageCache: load($key) 失败: ${it.message}")
            }.getOrNull()
        }

    internal suspend fun <T> save(
        key: String,
        serializer: kotlinx.serialization.SerializationStrategy<T>,
        value: T,
    ): Unit = withContext(Dispatchers.Default) {
        runCatching {
            val p = pathForKey(key) ?: return@runCatching
            writeBytesToPath(p, json.encodeToString(serializer, value).encodeToByteArray())
        }.onFailure {
            println("FoxFirstPageCache: save($key) 失败: ${it.message}")
        }
    }

    /** 删掉一条快照（会话删除等）。文件不存在也算成功。 */
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    internal fun remove(key: String) {
        runCatching {
            val p = pathForKey(key) ?: return
            NSFileManager.defaultManager.removeItemAtPath(p, null)
        }
    }
}

/** 首页第一页快照（DTO，全部可序列化）。 */
@Serializable
data class HomeFirstPageDto(
    val hotKeywords: List<HotKeyword> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val recentPlays: List<PlayHistoryDto> = emptyList(),
    val recommendPool: List<MusicDto> = emptyList(),
)

/** 发现页榜单详情项（用 list 而非 Map<Long,*>，避开 JSON Map 长整型 key 的序列化坑）。 */
@Serializable
data class RankDetailEntry(val rankId: Long, val detail: PlaylistDetailDto)

/** 发现页第一页快照（DTO，全部可序列化）。 */
@Serializable
data class DiscoverFirstPageDto(
    val hotKeywords: List<HotKeyword> = emptyList(),
    val ranks: List<PlaylistDto> = emptyList(),
    val rankDetails: List<RankDetailEntry> = emptyList(),
    val newMusic: List<MusicDto> = emptyList(),
    val hotArtists: List<ArtistDto> = emptyList(),
    val newMusicPool: List<MusicDto> = emptyList(),
)
