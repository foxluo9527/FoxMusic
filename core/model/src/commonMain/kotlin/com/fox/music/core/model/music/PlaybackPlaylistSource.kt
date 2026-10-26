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
    DOWNLOAD,
    STREAM,
    ARTIST,
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
    /** 是否可在播放列表弹窗展示来源并可点击跳转。 */
    val isDisplayable: Boolean
        get() = when (kind) {
            // 推荐/新歌是虚拟列表，用 DetailType.RECOMMEND_ID / NEW_MUSIC_ID 占位，也可跳详情页
            PlaybackSourceKind.PLAYLIST,
            PlaybackSourceKind.ALBUM,
            PlaybackSourceKind.RANK,
            PlaybackSourceKind.POST,
            PlaybackSourceKind.RECOMMEND,
            PlaybackSourceKind.NEW_MUSIC,
            -> id != null && id > 0L && !title.isNullOrBlank()
            // 这四类没有实体 id（搜索用关键词、下载/音流/歌手用标题），只要有标题即可展示
            PlaybackSourceKind.SEARCH,
            PlaybackSourceKind.DOWNLOAD,
            PlaybackSourceKind.STREAM,
            PlaybackSourceKind.ARTIST,
            -> !title.isNullOrBlank()
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
            PlaybackSourceKind.DOWNLOAD -> "下载"
            PlaybackSourceKind.STREAM -> "音流"
            PlaybackSourceKind.ARTIST -> "歌手"
            PlaybackSourceKind.HOME -> "首页"
            PlaybackSourceKind.DISCOVER -> "发现"
            PlaybackSourceKind.OTHER -> "其他"
        }

    fun toDetailType(): DetailType? = when (kind) {
        PlaybackSourceKind.PLAYLIST -> DetailType.PLAYLIST
        PlaybackSourceKind.ALBUM -> DetailType.ALBUM
        PlaybackSourceKind.RANK -> DetailType.RANK
        PlaybackSourceKind.ARTIST -> DetailType.ARTIST
        PlaybackSourceKind.RECOMMEND -> DetailType.RECOMMEND
        PlaybackSourceKind.NEW_MUSIC -> DetailType.NEW_MUSIC
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
                DetailType.ARTIST -> PlaybackSourceKind.ARTIST
            }
            // 推荐/新歌详情页的头部标题是运营文案，播放列表来源统一用板块名，和首页/发现页入口一致
            val sourceTitle = when (type) {
                DetailType.RECOMMEND -> "为你精选"
                DetailType.NEW_MUSIC -> "新歌速递"
                else -> title
            }
            return PlaybackPlaylistSource(
                kind = kind,
                id = id.takeIf { it > 0L },
                title = sourceTitle?.takeIf { it.isNotBlank() },
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
