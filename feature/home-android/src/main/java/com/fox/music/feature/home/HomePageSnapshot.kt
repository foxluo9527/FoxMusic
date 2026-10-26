package com.fox.music.feature.home

import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.model.music.Playlist
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 首页的「接口快照」：进入页面时先铺这一份（带 loading），网络数据回来后整体替换。
 *
 * [ownerUserId] 用于账号维度校验：最近在听是个人数据，换账号后不能拿上一个账号的快照顶上。
 */
@Serializable
internal data class HomePageSnapshot(
    val ownerUserId: Long?,
    val hotKeywords: List<HotKeyword>,
    val recentPlays: List<PlayHistory>,
    val recommendedPlaylists: List<Playlist>,
    val recommendedAlbums: List<Album>,
    /** 「为你精选」分页的首批（分页重建需要时间，先拿它顶上） */
    val recommendedMusic: List<Music>,
)

private val homeSnapshotJson = Json { ignoreUnknownKeys = true }

/**
 * 注册首页快照的磁盘编解码器：冷启动 / 进程重启后，TabPageCache 靠它把上一进程
 * 落盘的快照解析回来（无网络时首页照样能渲染上一次的数据）。
 */
@Suppress("UNCHECKED_CAST")
fun registerHomePageCacheCodec() {
    TabPageCache.register(
        key = TabPageKeys.HOME,
        encode = { value ->
            homeSnapshotJson.encodeToString(
                HomePageSnapshot.serializer(),
                value as HomePageSnapshot,
            )
        },
        decode = { raw ->
            runCatching {
                homeSnapshotJson.decodeFromString(HomePageSnapshot.serializer(), raw)
            }.getOrNull()
        },
    )
}
