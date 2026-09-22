package com.fox.music.core.model.music

import com.fox.music.core.model.social.Post
import kotlinx.serialization.Serializable

/**
 * 当前播放队列的创建来源。
 * 仅具备有效 id、标题的可展示来源会在播放列表弹窗显示并可跳转。
 */
@Serializable
enum class PlaybackSourceKind {
    PLAYLIST,
    ALBUM,
    RANK,
    POST,
    RECOMMEND,
    NEW_MUSIC,
    FAVORITE_MUSIC,
    PLAY_HISTORY,
    SEARCH,
    HOME,
    DISCOVER,
    OTHER,
}

@Serializable
data class PlaybackPlaylistSource(
    val kind: PlaybackSourceKind,
    val id: Long? = null,
    val title: String? = null,
    val coverUrl: String? = null,
) {
    val isDisplayable: Boolean
        get() = when (kind) {
            PlaybackSourceKind.PLAYLIST,
            PlaybackSourceKind.ALBUM,
            PlaybackSourceKind.RANK,
            PlaybackSourceKind.POST,
            -> id != null && id > 0L && !title.isNullOrBlank()
            else -> false
        }

    val typeLabel: String
        get() = when (kind) {
            PlaybackSourceKind.PLAYLIST -> "歌单"
            PlaybackSourceKind.ALBUM -> "专辑"
            PlaybackSourceKind.RANK -> "排行榜"
            PlaybackSourceKind.POST -> "帖子"
            PlaybackSourceKind.RECOMMEND -> "推荐"
            PlaybackSourceKind.NEW_MUSIC -> "最新"
            PlaybackSourceKind.FAVORITE_MUSIC -> "收藏"
            PlaybackSourceKind.PLAY_HISTORY -> "历史"
            PlaybackSourceKind.SEARCH -> "搜索"
            PlaybackSourceKind.HOME -> "首页"
            PlaybackSourceKind.DISCOVER -> "发现"
            PlaybackSourceKind.OTHER -> "其他"
        }

    fun toDetailType(): DetailType? = when (kind) {
        PlaybackSourceKind.PLAYLIST -> DetailType.PLAYLIST
        PlaybackSourceKind.ALBUM -> DetailType.ALBUM
        PlaybackSourceKind.RANK -> DetailType.RANK
        else -> null
    }

    companion object {
        fun fromDetailType(
            type: DetailType,
            id: Long,
            title: String?,
            coverUrl: String?,
        ): PlaybackPlaylistSource {
            val kind = when (type) {
                DetailType.PLAYLIST -> PlaybackSourceKind.PLAYLIST
                DetailType.ALBUM -> PlaybackSourceKind.ALBUM
                DetailType.RANK -> PlaybackSourceKind.RANK
                DetailType.RECOMMEND -> PlaybackSourceKind.RECOMMEND
                DetailType.NEW_MUSIC -> PlaybackSourceKind.NEW_MUSIC
                DetailType.FAVORITE_MUSIC -> PlaybackSourceKind.FAVORITE_MUSIC
                DetailType.PLAY_HISTORY -> PlaybackSourceKind.PLAY_HISTORY
            }
            return PlaybackPlaylistSource(
                kind = kind,
                id = id.takeIf { it > 0L },
                title = title?.takeIf { it.isNotBlank() },
                coverUrl = coverUrl?.takeIf { it.isNotBlank() },
            )
        }

        fun fromPost(post: Post): PlaybackPlaylistSource {
            val title = post.title
                ?.takeIf { it.isNotBlank() && it != "无标题" }
                ?: post.content.trim().take(40).ifBlank { "帖子" }
            val cover = post.displayMedia.firstOrNull()?.displayUrl
                ?: post.music?.coverImage
                ?: post.authorAvatar
            return PlaybackPlaylistSource(
                kind = PlaybackSourceKind.POST,
                id = post.id.takeIf { it > 0L },
                title = title,
                coverUrl = cover?.takeIf { it.isNotBlank() },
            )
        }
    }
}
