package com.fox.music.feature.chat.ui.util

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageType
import com.fox.music.feature.chat.ui.component.ChatMediaPreviewType

fun Message.resolveMediaPreviewType(): ChatMediaPreviewType? {
    val fileType = fileType?.lowercase()
    val fileName = localMediaFileName ?: parseFileContentName(content)
    return when {
        type == MessageType.IMAGE || fileType?.startsWith("image") == true || isImageFileName(fileName) -> ChatMediaPreviewType.IMAGE
        fileType?.startsWith("video") == true || isVideoFileName(fileName) -> ChatMediaPreviewType.VIDEO
        else -> null
    }
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
