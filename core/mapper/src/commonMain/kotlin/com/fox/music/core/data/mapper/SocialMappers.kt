package com.fox.music.core.data.mapper

import com.fox.music.core.model.music.Tag
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import com.fox.music.core.model.user.User
import com.fox.music.core.network.model.PostDto
import com.fox.music.core.network.model.PostLikerDto
import com.fox.music.core.network.model.PostMediaDto

/**
 * 社区（帖子）相关 DTO → 领域模型的跨端映射。
 *
 * 从 :core:data 的 ChatSocialMappers 移过来的原因：社区版块要迁到 iOS，
 * 而 :core:data 绑着 Retrofit/Room/Hilt（android.library），iOS 拿不到；
 * 映射本身零平台依赖，与 MusicDto.toMusic() 一样放 :core:mapper 供两端共用。
 * 包名保持 com.fox.music.core.data.mapper 不变 —— Android 侧调用点零改动。
 */
fun PostDto.toPost(): Post = Post(
    id = id,
    userId = userId,
    title = title,
    content = content,
    images = images,
    media = media.map { it.toPostMedia() },
    tags = when {
        tagItems.isNotEmpty() -> tagItems.map { it.toTag() }
        else -> tags.map { Tag(id = 0L, name = it) }
    },
    music = music?.toMusic(),
    likeCount = likeCount,
    commentCount = commentCount,
    shareCount = shareCount,
    collectionCount = collectionCount,
    isLiked = isLiked,
    isFavorited = isFavorited,
    createdAt = createdAt,
    updatedAt = updatedAt,
    authorName = authorName,
    authorAvatar = authorAvatar,
)

/** 点赞者 DTO → 用户（后端只给昵称+头像，昵称回落到"用户id"）。 */
fun PostLikerDto.toPostLikerUser(): User {
    val displayName = nickname?.takeIf { it.isNotBlank() } ?: "用户$id"
    return User(
        id = id,
        username = displayName,
        nickname = displayName,
        avatar = avatar,
    )
}

fun PostMediaDto.toPostMedia(): PostMedia {
    val mediaType = when (type.lowercase()) {
        "video" -> PostMediaType.VIDEO
        "live" -> PostMediaType.LIVE
        else -> PostMediaType.IMAGE
    }
    return PostMedia(
        type = mediaType,
        url = url,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
    )
}

fun PostMedia.toDto(): PostMediaDto = PostMediaDto(
    type = when (type) {
        PostMediaType.VIDEO -> "video"
        PostMediaType.LIVE -> "live"
        else -> "image"
    },
    url = url,
    videoUrl = videoUrl,
    coverUrl = coverUrl,
)
