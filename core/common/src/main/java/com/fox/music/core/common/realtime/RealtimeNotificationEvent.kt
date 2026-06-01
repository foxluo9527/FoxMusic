package com.fox.music.core.common.realtime

import com.fox.music.core.model.chat.NotificationType

data class RealtimeNotificationEvent(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    val route: String,
    val targetId: Long? = null,
    val targetType: String? = null,
    val peerUserId: Long? = null,
)

data class NavigationRequest(
    val route: String,
)
