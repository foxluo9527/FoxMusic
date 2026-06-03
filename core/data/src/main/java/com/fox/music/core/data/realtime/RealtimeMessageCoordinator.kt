package com.fox.music.core.data.realtime

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.common.realtime.RealtimeNotificationEvent
import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.data.mapper.toNotification
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.RealtimeConnectionLauncher
import com.fox.music.core.domain.repository.RealtimeNotificationDispatcher
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.network.websocket.WebSocketManager
import com.fox.music.core.network.websocket.WebSocketMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeMessageCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val webSocketManager: WebSocketManager,
    private val tokenManager: TokenManager,
    private val chatRepository: ChatRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationDispatcher: RealtimeNotificationDispatcher,
    private val activeChatTracker: ActiveChatTracker,
    private val notificationRouting: RealtimeNotificationRouting,
    private val connectionLauncher: RealtimeConnectionLauncher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private val recentlyDispatchedIds = ConcurrentHashMap.newKeySet<Long>()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun start() {
        if (started) return
        started = true

        registerNetworkCallback()

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

    fun onAppForeground() {
        scope.launch {
            webSocketManager.reconnect()
            chatRepository.syncUnreadMessages(peerUserId = 0)
            EventViewModel.notifyNotificationsUpdated()
        }
    }

    fun onAppBackground() {
        scope.launch {
            webSocketManager.ensureConnected()
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    webSocketManager.ensureConnected()
                }
            }
        })
    }

    private suspend fun handleMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.ChatMessage -> handleChatMessage(message.message)
            is WebSocketMessage.Notification -> handleNotification(message.notification)
            is WebSocketMessage.Error -> Timber.w("WebSocket error: ${message.error}")
            WebSocketMessage.Pong -> Unit
        }
    }

    private suspend fun handleChatMessage(dto: MessageDto) {
        chatRepository.ingestIncomingMessage(dto.toMessage())

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
            chatRepository.landMessageNotification(notification)
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
        if (!recentlyDispatchedIds.add(dedupeKey)) return
        scope.launch {
            delay(30_000)
            recentlyDispatchedIds.remove(dedupeKey)
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
}
