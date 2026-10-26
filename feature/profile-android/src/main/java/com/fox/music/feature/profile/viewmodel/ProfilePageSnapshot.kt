package com.fox.music.feature.profile.viewmodel

import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ListeningStats
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.user.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「我的」页的「接口快照」：进入页面先铺这一份（带后台刷新指示），新数据回来后整体替换。
 *
 * 整页都是个人数据（资料 / 歌单 / 收藏 / 听歌统计），因此带 [ownerUserId]：
 * 换账号登录时不得拿上一个账号的快照顶上（宁可走一次正常加载）。
 */
@Serializable
internal data class ProfilePageSnapshot(
    val ownerUserId: Long?,
    val user: User,
    val playlists: List<Playlist>,
    val favoriteMusicTotal: Int,
    val favoritePlaylists: List<Playlist>,
    val favoritePlaylistTotal: Int,
    val favoriteAlbums: List<Album>,
    val favoriteAlbumTotal: Int,
    val favoriteArtists: List<Artist>,
    val favoriteArtistTotal: Int,
    val playHistoryTotal: Int,
    val listeningStats: ListeningStats?,
)

private val profileSnapshotJson = Json { ignoreUnknownKeys = true }

/** 注册「我的」页快照的磁盘编解码器（冷启动 / 无网络时解析上一进程落盘的快照）。 */
@Suppress("UNCHECKED_CAST")
fun registerProfilePageCacheCodec() {
    TabPageCache.register(
        key = TabPageKeys.PROFILE,
        encode = { value ->
            profileSnapshotJson.encodeToString(
                ProfilePageSnapshot.serializer(),
                value as ProfilePageSnapshot,
            )
        },
        decode = { raw ->
            runCatching {
                profileSnapshotJson.decodeFromString(ProfilePageSnapshot.serializer(), raw)
            }.getOrNull()
        },
    )
}
