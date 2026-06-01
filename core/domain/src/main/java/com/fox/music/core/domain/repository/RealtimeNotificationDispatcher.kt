package com.fox.music.core.domain.repository

import com.fox.music.core.model.chat.Notification

interface RealtimeNotificationDispatcher {
    fun showNotification(
        notification: Notification,
        route: String,
        peerUserId: Long? = null,
    )
}
