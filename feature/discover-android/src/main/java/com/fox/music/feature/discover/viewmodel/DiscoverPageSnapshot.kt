package com.fox.music.feature.discover

import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 发现页的「接口快照」：进入页面先铺这一份（带刷新指示），新数据回来后整体替换。
 *
 * 榜单详情是「先拿榜单列表、再逐个拉详情」的两段式请求，快照把结果一起存下来，
 * 回到发现页不会先出现一排空榜单卡。
 */
@Serializable
internal data class DiscoverPageSnapshot(
    val hotKeywords: List<HotKeyword>,
    val ranks: List<Playlist>,
    val rankDetails: Map<Long, PlaylistDetail>,
    val newMusic: List<Music>,
    val hotArtists: List<Artist>,
)

private val discoverSnapshotJson = Json { ignoreUnknownKeys = true }

/** 注册发现页快照的磁盘编解码器（冷启动 / 无网络时解析上一进程落盘的快照）。 */
@Suppress("UNCHECKED_CAST")
fun registerDiscoverPageCacheCodec() {
    TabPageCache.register(
        key = TabPageKeys.DISCOVER,
        encode = { value ->
            discoverSnapshotJson.encodeToString(
                DiscoverPageSnapshot.serializer(),
                value as DiscoverPageSnapshot,
            )
        },
        decode = { raw ->
            runCatching {
                discoverSnapshotJson.decodeFromString(DiscoverPageSnapshot.serializer(), raw)
            }.getOrNull()
        },
    )
}
