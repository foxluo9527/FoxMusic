package com.fox.music.feature.chat.util

import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.feature.chat.viewmodel.NotificationCategory

object NotificationFilter {

    /** 通知分类页的三个分类各取哪些类型（两端共用，避免归类口径跑偏）。 */
    fun filterFor(
        category: NotificationCategory,
        notifications: List<Notification>,
    ): List<Notification> = when (category) {
        NotificationCategory.COMMENT -> commentNotifications(notifications)
        NotificationCategory.LIKE -> likeNotifications(notifications)
        NotificationCategory.SYSTEM -> systemNotifications(notifications)
    }

    /** 通知分类页可展示的类型（排除私信/聊天消息通知） */
    private val CATEGORY_DISPLAY_TYPES = setOf(
        NotificationType.SYSTEM,
        NotificationType.COMMENT,
        NotificationType.LIKE,
        NotificationType.MUSIC,
        NotificationType.FOLLOW,
        NotificationType.MENTION,
        NotificationType.FRIEND_REQUEST,
    )

    fun displayableNotifications(notifications: List<Notification>): List<Notification> =
        notifications.filter { it.type in CATEGORY_DISPLAY_TYPES }

    fun commentNotifications(notifications: List<Notification>): List<Notification> =
        displayableNotifications(notifications).filter {
            it.type == NotificationType.COMMENT || it.type == NotificationType.MENTION
        }

    fun systemNotifications(notifications: List<Notification>): List<Notification> =
        displayableNotifications(notifications).filter { it.type == NotificationType.SYSTEM }

    fun likeNotifications(notifications: List<Notification>): List<Notification> =
        displayableNotifications(notifications).filter {
            it.type == NotificationType.LIKE || it.type == NotificationType.MUSIC
        }

    fun previewText(notifications: List<Notification>): String? {
        val unread = notifications.firstOrNull { !it.isRead }
        val latest = unread ?: notifications.firstOrNull()
        return latest?.content?.takeIf { it.isNotBlank() }
    }

    fun unreadCount(notifications: List<Notification>): Int =
        notifications.count { !it.isRead }
}
