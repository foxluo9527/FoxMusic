package com.fox.music.core.data.realtime

import com.fox.music.core.common.EventViewModel
import com.fox.music.core.common.network.NetworkMonitor
import com.fox.music.core.common.realtime.RealtimeNotificationEvent
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.common.util.FoxLog
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.data.mapper.toNotification
import com.fox.music.core.domain.repository.RealtimeConnectionLauncher
import com.fox.music.core.domain.repository.RealtimeNotificationDispatcher
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto
import com.fox.music.core.network.websocket.WebSocketManager
import com.fox.music.core.network.websocket.WebSocketMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 实时消息协调器：把 WebSocket 收到的聊天/通知落库，并按「前台 / 后台」分流提醒方式。
 *
 * 从 Android 专属下沉到 commonMain，两端语义完全一致：
 *   - 前台：发出 in-app 事件，由 [EventViewModel.inAppNotificationEvents] 驱动应用内横幅
 *   - 后台：交给平台侧的 [RealtimeNotificationDispatcher]（Android 弹系统通知；iOS 当前为空实现）
 *
 * Android → 跨端的四处替换：
 *   1. `Context` + `ConnectivityManager.NetworkCallback` → 可选注入的 [NetworkMonitor]
 *   2. `Timber` → [FoxLog]
 *   3. `java.util.concurrent.ConcurrentHashMap` → `MutableSet` + [Mutex]（K/N 无此并发集合）
 *   4. `@Inject/@Singleton/@ApplicationContext` → 普通构造器，由平台装配层手工创建
 *
 * iOS 平台限制：App 进入后台会被系统挂起，WebSocket 随之断开，因此后台期间
 * **收不到新消息** —— 这是平台行为，唯一后台通道是 APNs（本项目明确不做）。
 * 兜底手段是回前台时的 [onAppForeground]（重连 + 补拉未读），消息不丢，只是延迟。
 */
class RealtimeMessageCoordinator(
    private val webSocketManager: WebSocketManager,
    private val tokenManager: TokenManager,
    private val messageGateway: RealtimeMessageGateway,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationDispatcher: RealtimeNotificationDispatcher,
    private val activeChatTracker: ActiveChatTracker,
    private val notificationRouting: RealtimeNotificationRouting,
    private val connectionLauncher: RealtimeConnectionLauncher,
    /** 可选：网络恢复时自动重连。iOS 端可以不传。 */
    private val networkMonitor: NetworkMonitor? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var networkJob: Job? = null
    private val dedupeMutex = Mutex()
    private val recentlyDispatchedIds = mutableSetOf<Long>()

    fun start() {
        if (started) return
        started = true

        observeNetworkRecovery()

        tokenManager.isLoggedIn
            .onEach { loggedIn ->
                if (loggedIn) {
                    connectionLauncher.startConnectionService()
                } else {
                    connectionLauncher.stopConnectionService()
                    webSocketManager.disconnect()
                }
            }
            .launchIn(scope)

        webSocketManager.incomingMessages
            .onEach { message -> handleMessage(message) }
            .launchIn(scope)
    }

    fun stop() {
        networkJob?.cancel()
        networkJob = null
        started = false
    }

    /**
     * 回前台：重连 + 补拉未读。
     *
     * 这是 iOS 上「退出期间消息不丢」的唯一保障 —— 后台挂起期间的内容，
     * 靠这里的 [ChatRepository.syncUnreadMessages] 一次性拉回来。
     */
    fun onAppForeground() {
        scope.launch {
            webSocketManager.ensureConnected()
            runCatching { messageGateway.syncUnread() }
                .onFailure { FoxLog.w(TAG, "syncUnread failed: ${it.message}") }
            EventViewModel.notifyNotificationsUpdated()
        }
    }

    fun onAppBackground() {
        scope.launch {
            // Android 靠前台服务继续保活；iOS 挂起后连接必然断开，此调用无害。
            webSocketManager.ensureConnected()
        }
    }

    private fun observeNetworkRecovery() {
        val monitor = networkMonitor ?: return
        if (networkJob != null) return
        networkJob = monitor.isOnline
            .onEach { online -> if (online) webSocketManager.ensureConnected() }
            .launchIn(scope)
    }

    private suspend fun handleMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.ChatMessage -> handleChatMessage(message.message)
            is WebSocketMessage.Notification -> handleNotification(message.notification)
            is WebSocketMessage.Error -> FoxLog.w(TAG, "WebSocket error: ${message.error}")
            WebSocketMessage.Pong -> Unit
        }
    }

    private suspend fun handleChatMessage(dto: MessageDto) {
        messageGateway.onIncomingMessage(dto.toMessage())

        val currentUserId = userPreferencesRepository.userPreferences.first().userId ?: return
        if (dto.senderId == currentUserId) return

        notifyInboxUpdated()

        val peerId = dto.senderId
        if (activeChatTracker.currentPeerUserId.value == peerId) return

        val title = dto.senderNickname?.takeIf { it.isNotBlank() } ?: "新消息"
        val content = previewForMessage(dto.content, dto.type)
        val route = notificationRouting.chatRoute(peerId)
        dispatchReminder(
            id = dto.id,
            type = NotificationType.MESSAGE,
            title = title,
            content = content,
            route = route,
            peerUserId = peerId,
            dedupeKey = dto.id,
        )
    }

    private suspend fun handleNotification(dto: NotificationDto) {
        val notification = dto.toNotification()
        if (notification.type == NotificationType.MESSAGE) {
            messageGateway.onMessageNotification(notification)
            notifyInboxUpdated()
        }
        val peerUserId = if (notification.type == NotificationType.MESSAGE) {
            NotificationPeerResolver.resolvePeerUserId(notification)
        } else {
            null
        }
        val linkedMessageId = NotificationPeerResolver.resolveMessageId(notification)
        val route = notificationRouting.routeFor(notification)
        dispatchReminder(
            id = notification.id,
            type = notification.type,
            title = notification.title,
            content = notification.content,
            route = route,
            targetId = notification.targetId,
            targetType = notification.targetType,
            peerUserId = peerUserId?.takeIf { it > 0 },
            dedupeKey = linkedMessageId ?: notification.id,
        )
        if (notification.type != NotificationType.MESSAGE) {
            notifyInboxUpdated()
        }
    }

    private fun notifyInboxUpdated() {
        EventViewModel.notifyNotificationsUpdated()
    }

    private suspend fun dispatchReminder(
        id: Long,
        type: NotificationType,
        title: String,
        content: String,
        route: String,
        targetId: Long? = null,
        targetType: String? = null,
        peerUserId: Long? = null,
        dedupeKey: Long = id,
    ) {
        val isNew = dedupeMutex.withLock { recentlyDispatchedIds.add(dedupeKey) }
        if (!isNew) return
        scope.launch {
            delay(DEDUPE_WINDOW_MS)
            dedupeMutex.withLock { recentlyDispatchedIds.remove(dedupeKey) }
        }

        val prefs = userPreferencesRepository.userPreferences.first()
        if (!prefs.notificationSettings.isEnabled(type)) return

        val event = RealtimeNotificationEvent(
            id = id,
            type = type,
            title = title,
            content = content,
            route = route,
            targetId = targetId,
            targetType = targetType,
            peerUserId = peerUserId,
        )
        val inForeground = EventViewModel.appInForeground.value == true
        if (inForeground) {
            EventViewModel.emitInAppNotification(event)
        } else {
            notificationDispatcher.showNotification(
                notification = Notification(
                    id = id,
                    type = type,
                    title = title,
                    content = content,
                    senderId = peerUserId,
                    targetId = targetId,
                    targetType = targetType,
                ),
                route = route,
                peerUserId = peerUserId,
            )
        }
    }

    companion object {
        private const val TAG = "RealtimeCoordinator"
        private const val DEDUPE_WINDOW_MS = 30_000L
    }
}
