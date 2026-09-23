package com.fox.music.feature.chat.ui.util

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import com.fox.music.feature.chat.ui.component.ChatMediaPreviewType

data class ChatMessageMediaViewer(
    val previewType: ChatMediaPreviewType,
    val mediaUrl: String,
    val videoUrl: String? = null,
    val fileName: String? = null,
    val messageLocalId: String? = null,
)

fun ChatMessageMediaViewer.toPostMedia(): PostMedia = when (previewType) {
    ChatMediaPreviewType.IMAGE -> PostMedia(
        type = PostMediaType.IMAGE,
        url = mediaUrl,
    )
    ChatMediaPreviewType.VIDEO -> PostMedia(
        type = PostMediaType.VIDEO,
        url = mediaUrl,
        videoUrl = mediaUrl,
    )
    ChatMediaPreviewType.LIVE -> PostMedia(
        type = PostMediaType.LIVE,
        url = mediaUrl,
        videoUrl = videoUrl,
    )
}

fun Message.toChatMessageMediaViewer(): ChatMessageMediaViewer? {
    if (isRecalled) return null
    val previewType = resolveMediaPreviewType() ?: return null
    val mediaUrl = resolveMediaPreviewUrl() ?: return null
    return ChatMessageMediaViewer(
        previewType = previewType,
        mediaUrl = mediaUrl,
        videoUrl = resolveVideoPreviewUrl(),
        fileName = localMediaFileName ?: parseFileContentName(content),
        messageLocalId = localId,
    )
}

fun List<Message>.buildChatMediaPreviewQueue(): List<ChatMessageMediaViewer> =
    mapNotNull { it.toChatMessageMediaViewer() }

fun List<ChatMessageMediaViewer>.indexOfViewer(viewer: ChatMessageMediaViewer): Int {
    viewer.messageLocalId?.let { localId ->
        indexOfFirst { it.messageLocalId == localId }.takeIf { it >= 0 }?.let { return it }
    }
    return indexOfFirst {
        it.previewType == viewer.previewType &&
            it.mediaUrl == viewer.mediaUrl &&
            it.videoUrl == viewer.videoUrl
    }
}

fun Message.resolveMediaPreviewType(): ChatMediaPreviewType? {
    if (isRecalled) return null
    if (!videoUrl.isNullOrBlank() || !localVideoUri.isNullOrBlank()) {
        return ChatMediaPreviewType.LIVE
    }
    val fileType = fileType?.lowercase()
    val fileName = localMediaFileName ?: parseFileContentName(content)
    return when {
        type == MessageType.IMAGE || fileType?.startsWith("image") == true || isImageFileName(fileName) -> ChatMediaPreviewType.IMAGE
        fileType?.startsWith("video") == true || isVideoFileName(fileName) -> ChatMediaPreviewType.VIDEO
        else -> null
    }
}

fun Message.resolveVideoPreviewUrl(): String? {
    videoUrl?.takeIf { it.isNotBlank() }?.let { return MediaUrlResolver.resolve(it) }
    localVideoUri?.takeIf { it.isNotBlank() }?.let { return MediaUrlResolver.resolve(it) }
    return null
}

fun Message.resolveMediaPreviewUrl(): String? {
    localMediaUri?.takeIf { it.isNotBlank() }?.let { return MediaUrlResolver.resolve(it) }
    remoteMediaUrl?.takeIf { it.isNotBlank() }?.let { return MediaUrlResolver.resolve(it) }
    if (content.startsWith("[file]", ignoreCase = true) ||
        content.startsWith("[audio]", ignoreCase = true)
    ) {
        return null
    }
    return MediaUrlResolver.resolve(content)
}

fun parseFileContentName(content: String): String {
    if (content.startsWith("[file]", ignoreCase = true)) {
        return content.removePrefix("[file]").ifBlank { "文件" }
    }
    if (content.startsWith("[audio]", ignoreCase = true)) {
        return content.removePrefix("[audio]").ifBlank { "语音" }
    }
    return content.substringAfterLast('/').ifBlank { "文件" }
}

private fun isImageFileName(value: String): Boolean {
    val lower = value.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
        lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
}

private fun isVideoFileName(value: String): Boolean {
    val lower = value.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
        lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
}
