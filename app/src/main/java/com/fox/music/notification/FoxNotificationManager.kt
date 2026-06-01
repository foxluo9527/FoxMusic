package com.fox.music.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fox.music.MainActivity
import com.fox.music.R
import com.fox.music.core.domain.repository.RealtimeNotificationDispatcher
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoxNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : RealtimeNotificationDispatcher {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(
                CHANNEL_SOCIAL,
                "社交互动",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                CHANNEL_CHAT,
                "聊天消息",
                NotificationManager.IMPORTANCE_HIGH,
            ),
            NotificationChannel(
                CHANNEL_SYSTEM,
                "系统通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        ).forEach(manager::createNotificationChannel)
    }

    override fun showNotification(
        notification: Notification,
        route: String,
        peerUserId: Long?,
    ) {
        val nm = NotificationManagerCompat.from(context)
        val notificationsEnabled = nm.areNotificationsEnabled()
        val hasPostPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!notificationsEnabled || !hasPostPermission) return

        val channelId = channelFor(notification.type)
        val notifyId = (notification.id xor notification.content.hashCode().toLong())
            .toInt()
            .let { if (it == 0) notification.id.toInt() else it }
            .and(0x7FFFFFFF)
        val contentIntent = PendingIntent.getActivity(
            context,
            notifyId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_START_ROUTE, route)
                putExtra(EXTRA_NOTIFICATION_ID, notification.id)
                peerUserId?.takeIf { it > 0 }?.let { putExtra(EXTRA_PEER_USER_ID, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val built = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title.ifBlank { defaultTitle(notification.type) })
            .setContentText(notification.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.content))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent)
            .setPriority(
                if (notification.type == NotificationType.MESSAGE) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .build()

        try {
            nm.notify(notifyId, built)
        } catch (_: SecurityException) {
        }
    }

    private fun channelFor(type: NotificationType): String = when (type) {
        NotificationType.MESSAGE -> CHANNEL_CHAT
        NotificationType.SYSTEM -> CHANNEL_SYSTEM
        else -> CHANNEL_SOCIAL
    }

    private fun defaultTitle(type: NotificationType): String = when (type) {
        NotificationType.COMMENT -> "新评论提醒"
        NotificationType.LIKE -> "点赞提醒"
        NotificationType.FOLLOW -> "关注提醒"
        NotificationType.MENTION -> "提及提醒"
        NotificationType.MESSAGE -> "聊天消息"
        NotificationType.FRIEND_REQUEST -> "好友请求"
        NotificationType.MUSIC -> "音乐提醒"
        NotificationType.SYSTEM -> "系统通知"
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_PEER_USER_ID = "peer_user_id"

        private const val CHANNEL_SOCIAL = "fox_social_channel"
        private const val CHANNEL_CHAT = "fox_chat_channel"
        private const val CHANNEL_SYSTEM = "fox_system_channel"
    }
}
