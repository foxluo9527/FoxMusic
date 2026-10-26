package com.fox.music.feature.stream

import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 音流会话的快照（内存 + 磁盘）。
 *
 * 与 [StreamSession]（DataStore、只有 id 序列、冷启动要靠同 seed 重拉复现）不同，
 * 这份快照直接持有已经加载到内存的整批卡片，因此**重建页面时不需要再打接口、
 * 也不需要 Loading**：直接把上一批数据铺上去，并按 [anchorIndex] / [anchorPositionMs]
 * 续上上次听到的位置。落盘后应用重启（崩溃恢复 / 手动杀进程）也能直接复用上一批，
 * 只有文件缺失或过期才回落到 [StreamSession] 的重放路径。
 */
@Serializable
internal data class StreamPageSnapshot(
    val ownerUserId: Long?,
    val mood: String?,
    val items: List<StreamItemUi>,
    val seed: String,
    /** 首屏请求时的 exclude：后续加载更多要原样沿用，否则分页口径漂移 */
    val baseExcludeIds: List<Long>,
    /** 本会话已听过的歌曲 id：作为 exclude 下发，避免重复推荐 */
    val heardIds: Set<Long>,
    /** 上次听到的第几张卡片 */
    val anchorIndex: Int,
    /** 上次听到该歌曲的位置（整曲绝对毫秒） */
    val anchorPositionMs: Long,
    val canLoadMore: Boolean,
)

private val streamSnapshotJson = Json { ignoreUnknownKeys = true }

/** 注册音流快照的磁盘编解码器（冷启动 / 无网络时解析上一进程落盘的快照）。 */
@Suppress("UNCHECKED_CAST")
fun registerStreamPageCacheCodec() {
    TabPageCache.register(
        key = TabPageKeys.STREAM,
        encode = { value ->
            streamSnapshotJson.encodeToString(
                StreamPageSnapshot.serializer(),
                value as StreamPageSnapshot,
            )
        },
        decode = { raw ->
            runCatching {
                streamSnapshotJson.decodeFromString(StreamPageSnapshot.serializer(), raw)
            }.getOrNull()
        },
    )
}
