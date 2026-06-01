package com.fox.music.notification

import com.fox.music.core.data.realtime.NotificationPeerResolver
import com.fox.music.core.data.realtime.RealtimeNotificationRouting
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.feature.chat.FRIENDS_ROUTE
import com.fox.music.feature.chat.MESSAGES_ROUTE
import com.fox.music.feature.chat.chatDetailRoute
import com.fox.music.feature.chat.notificationCategoryRoute
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRouting @Inject constructor() : RealtimeNotificationRouting {

    override fun routeFor(notification: Notification): String = when (notification.type) {
        NotificationType.MESSAGE -> {
            val peerId = NotificationPeerResolver.resolvePeerUserId(notification)
            if (peerId > 0L) chatRoute(peerId) else MESSAGES_ROUTE
        }
        NotificationType.COMMENT -> notificationCategoryRoute("comment")
        NotificationType.LIKE, NotificationType.MUSIC -> notificationCategoryRoute("like")
        NotificationType.FOLLOW -> MESSAGES_ROUTE
        NotificationType.MENTION -> notificationCategoryRoute("comment")
        NotificationType.FRIEND_REQUEST -> FRIENDS_ROUTE
        NotificationType.SYSTEM -> notificationCategoryRoute("system")
    }

    override fun chatRoute(peerUserId: Long): String = chatDetailRoute(peerUserId)
}
