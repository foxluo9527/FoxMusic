package com.fox.music.realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.fox.music.MainActivity
import com.fox.music.R
import com.fox.music.core.network.websocket.ConnectionState
import com.fox.music.core.network.websocket.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台 Service 保活 WebSocket 长连接。
 * 使用 [remoteMessaging] 类型以符合 Android 14+ 对即时消息前台服务的约束。
 */
@AndroidEntryPoint
class RealtimeConnectionService : Service() {

    @Inject
    lateinit var webSocketManager: WebSocketManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null
    private var connectionStateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    webSocketManager.disconnect()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification(resolveNotificationText())
        startForegroundWithType(notification)

        observeConnectionStateForNotification()
        startConnectionWatchdog()

        if (!connectionStarted) {
            connectionStarted = true
            scope.launch {
                webSocketManager.ensureConnected()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        connectionStateJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithType(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeConnectionStateForNotification() {
        connectionStateJob?.cancel()
        connectionStateJob = webSocketManager.connectionState
            .onEach { updateForegroundNotification(resolveNotificationText(it)) }
            .launchIn(scope)
    }

    private fun resolveNotificationText(state: ConnectionState? = null): String {
        val resolved = state ?: run {
            when {
                webSocketManager.isConnected() -> ConnectionState.CONNECTED
                else -> ConnectionState.DISCONNECTED
            }
        }
        return when (resolved) {
            ConnectionState.CONNECTED -> getString(R.string.realtime_notification_connected)
            ConnectionState.CONNECTING -> getString(R.string.realtime_notification_connecting)
            ConnectionState.DISCONNECTING,
            ConnectionState.DISCONNECTED,
            ConnectionState.FAILED,
            -> getString(R.string.realtime_notification_disconnected)
        }
    }

    private fun updateForegroundNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, type)
        }
    }

    /** 兜底：进程未被 OEM 冻结时尽快恢复连接 */
    private fun startConnectionWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                renewWakeLock()
                if (!webSocketManager.isConnected()) {
                    webSocketManager.ensureConnected()
                }
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FoxMusic:RealtimeConnection",
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_HOLD_MS)
        }
    }

    private fun renewWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
        }
        lock.acquire(WAKE_LOCK_HOLD_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "消息连接",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.realtime_notification_battery_hint)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.realtime_notification_title))
        .setContentText(contentText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    companion object {
        /** v2：提高重要性，避免部分 ROM 将低优先级前台服务与网络一并限制 */
        private const val CHANNEL_ID = "fox_realtime_connection_channel_v2"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "com.fox.music.realtime.STOP"
        private const val WATCHDOG_INTERVAL_MS = 15_000L
        private const val WAKE_LOCK_HOLD_MS = 10 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, RealtimeConnectionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RealtimeConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
