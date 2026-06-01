package com.fox.music.core.data.realtime

import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType

object NotificationPeerResolver {
    fun resolvePeerUserId(notification: Notification): Long {
        notification.sender?.id?.takeIf { it > 0 }?.let { return it }
        notification.senderId?.takeIf { it > 0 }?.let { return it }
        val targetType = notification.targetType?.lowercase()
        if (targetType in PEER_USER_TARGET_TYPES) {
            notification.targetId?.takeIf { it > 0 }?.let { return it }
        }
        return 0L
    }

    /** target_type=message 时 target_id 为消息 ID，非用户 ID */
    fun resolveMessageId(notification: Notification): Long? {
        if (notification.targetType?.lowercase() == "message") {
            return notification.targetId?.takeIf { it > 0 }
        }
        return null
    }

    fun previewFromContent(content: String): String {
        val colonIndex = content.indexOf(": ")
        return if (colonIndex in 1 until content.lastIndex) {
            content.substring(colonIndex + 2).trim().ifBlank { content }
        } else {
            content
        }
    }

    fun nicknameFromContent(content: String): String? {
        val colonIndex = content.indexOf(": ")
        if (colonIndex <= 0) return null
        return content.substring(0, colonIndex).trim().takeIf { it.isNotBlank() }
    }

    private val PEER_USER_TARGET_TYPES = setOf(
        "user",
        "chat",
        "private_message",
    )
}
