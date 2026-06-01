package com.fox.music.core.data.realtime

import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType

/**
 * 路由映射抽象，由 app 模块提供具体实现（依赖 feature 路由常量）。
 */
interface RealtimeNotificationRouting {
    fun routeFor(notification: Notification): String
    fun chatRoute(peerUserId: Long): String
}
