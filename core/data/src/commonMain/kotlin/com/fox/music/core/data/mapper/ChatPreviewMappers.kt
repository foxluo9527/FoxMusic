package com.fox.music.core.data.mapper

import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.chat.ShareData
import com.fox.music.core.model.music.Artist
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.ShareDataDto

/**
 * 聊天消息的纯映射：零平台依赖，从 :core:data-android 的 ChatLocalMappers.kt 抽出来。
 *
 * 抽出的原因：RealtimeMessageCoordinator 已下沉到 commonMain，它要用
 * [previewForMessage] 生成通知摘要；而 ChatLocalMappers 的其余部分依赖 Room 实体
 * （MessageEntity / ConversationEntity），只能留在 Android 侧。
 *
 * 包名与原来一致（com.fox.music.core.data.mapper），老调用点零改动。
 */

fun previewForMessage(content: String, type: String): String = when (type.lowercase()) {
    "image" -> "[图片]"
    "audio", "voice" -> "[语音]"
    "video" -> "[视频]"
    "file" -> when {
        isVideoUrl(content) -> "[视频]"
        isImageFileName(content) -> "[图片]"
        else -> "[文件]"
    }
    "music" -> "[音乐]"
    "share" -> "[分享]"
    else -> content.take(50)
}

fun previewForMessage(message: Message): String = when (message.type) {
    MessageType.IMAGE -> "[图片]"
    MessageType.AUDIO -> "[语音]"
    MessageType.FILE -> when {
        isVideoUrl(message.content) || message.fileType?.equals("video", ignoreCase = true) == true -> "[视频]"
        isImageFileName(message.content) || message.fileType?.equals("image", ignoreCase = true) == true -> "[图片]"
        else -> "[文件]"
    }
    MessageType.MUSIC -> "[音乐]"
    MessageType.SHARE -> "[分享]"
    MessageType.TEXT -> message.content.take(50)
}

private fun isVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
        lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
}

private fun isImageFileName(value: String): Boolean {
    val name = if (value.startsWith("[file]", ignoreCase = true)) {
        value.removePrefix("[file]")
    } else {
        value.substringAfterLast('/')
    }.lowercase()
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp")
}

// MessageDto 的字段归一下沉 —— ChatSocialMappers.toMessage() 与 ChatLocalMappers
// 的 Room 映射都要用，原先定义在 Android 侧的 ChatLocalMappers.kt。

fun MessageDto.resolveStatus(): String =
    status?.takeIf { it.isNotBlank() } ?: if (isRead) "read" else "sent"

fun MessageDto.resolveSentAt(): String? = sentAt ?: createdAt

fun MessageDto.resolveIsRecalled(): Boolean = isRecalled || isDeleted

/** 将服务端 share_data DTO 转为领域模型 */
fun ShareDataDto.toDomain(): ShareData {
    val resolvedCover = coverImage?.takeIf { it.isNotBlank() }
        ?: images.firstOrNull { it.isNotBlank() }
        ?: authorAvatar?.takeIf { it.isNotBlank() }
        ?: avatar?.takeIf { it.isNotBlank() }
    val resolvedDescription = description?.takeIf { it.isNotBlank() }
        ?: content?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
    return ShareData(
        id = id,
        title = title,
        name = name,
        coverImage = resolvedCover,
        avatar = avatar ?: authorAvatar,
        description = resolvedDescription,
        content = content,
        images = images,
        authorAvatar = authorAvatar,
        authorName = authorName,
        url = url,
        duration = duration,
        trackCount = trackCount,
        playCount = playCount,
        favoriteCount = favoriteCount,
        lyrics = lyrics,
        lyricsTrans = lyricsTrans,
        lyricsUrl = lyricsUrl,
        artists = artists.map { dto ->
            Artist(
                id = dto.id,
                name = dto.name,
                avatar = dto.avatar,
                coverImage = dto.coverImage,
            )
        },
    )
}
