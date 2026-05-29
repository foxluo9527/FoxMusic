package com.fox.music.feature.chat.util

import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType

object NotificationFilter {

    fun commentNotifications(notifications: List<Notification>): List<Notification> =
        notifications.filter { it.type == NotificationType.COMMENT }

    fun systemNotifications(notifications: List<Notification>): List<Notification> =
        notifications.filter { it.type == NotificationType.SYSTEM }

    fun likeNotifications(notifications: List<Notification>): List<Notification> =
        notifications.filter {
            it.type == NotificationType.LIKE ||
                it.type == NotificationType.MUSIC
        }

    fun previewText(notifications: List<Notification>): String? {
        val unread = notifications.firstOrNull { !it.isRead }
        val latest = unread ?: notifications.firstOrNull()
        return latest?.content?.takeIf { it.isNotBlank() }
    }

    fun unreadCount(notifications: List<Notification>): Int =
        notifications.count { !it.isRead }
}
