package com.fox.music.core.model.user

import com.fox.music.core.model.chat.NotificationType

/** 各类型消息提醒开关（前台横幅与后台系统通知共用） */
data class NotificationPreferenceSettings(
    val commentEnabled: Boolean = true,
    val likeEnabled: Boolean = true,
    val followEnabled: Boolean = true,
    val mentionEnabled: Boolean = true,
    val messageEnabled: Boolean = true,
    val friendRequestEnabled: Boolean = true,
    val systemEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
) {
    fun isEnabled(type: NotificationType): Boolean = when (type) {
        NotificationType.COMMENT -> commentEnabled
        NotificationType.LIKE -> likeEnabled
        NotificationType.FOLLOW -> followEnabled
        NotificationType.MENTION -> mentionEnabled
        NotificationType.MESSAGE -> messageEnabled
        NotificationType.FRIEND_REQUEST -> friendRequestEnabled
        NotificationType.SYSTEM -> systemEnabled
        NotificationType.MUSIC -> musicEnabled
    }

    fun withEnabled(type: NotificationType, enabled: Boolean): NotificationPreferenceSettings = when (type) {
        NotificationType.COMMENT -> copy(commentEnabled = enabled)
        NotificationType.LIKE -> copy(likeEnabled = enabled)
        NotificationType.FOLLOW -> copy(followEnabled = enabled)
        NotificationType.MENTION -> copy(mentionEnabled = enabled)
        NotificationType.MESSAGE -> copy(messageEnabled = enabled)
        NotificationType.FRIEND_REQUEST -> copy(friendRequestEnabled = enabled)
        NotificationType.SYSTEM -> copy(systemEnabled = enabled)
        NotificationType.MUSIC -> copy(musicEnabled = enabled)
    }
}
