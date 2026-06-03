package com.fox.music.core.network.websocket

import com.fox.music.core.network.BuildConfig
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto
import com.fox.music.core.network.di.WebSocketClient
import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManagerImpl @Inject constructor(
    @WebSocketClient private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
    private val json: Json,
) : WebSocketManager {

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectAttempts = 0
    private var intentionalDisconnect = false
    private val connectMutex = Mutex()
    /** 每次 doConnect 递增，用于忽略已取消的旧 WebSocket 回调 */
    private var connectGeneration = 0L

    private val wsUrl: String = BuildConfig.BASE_URL
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/')
        .plus("/ws")

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 15
        private const val RECONNECT_INTERVAL_MS = 2_000L
        private const val PING_INTERVAL_MS = 25_000L

        private val NOTIFICATION_TYPES = setOf(
            "comment",
            "like",
            "follow",
            "mention",
            "chat",
            "system",
            "friend_request",
            "music",
            "message",
        )
    }

    override suspend fun connect() {
        doConnect(resetReconnectAttempts = false)
    }

    override suspend fun ensureConnected() {
        intentionalDisconnect = false
        doConnect(resetReconnectAttempts = true)
    }

    private suspend fun doConnect(resetReconnectAttempts: Boolean) {
        connectMutex.withLock {
            if (_connectionState.value == ConnectionState.CONNECTED ||
                _connectionState.value == ConnectionState.CONNECTING
            ) {
                return
            }

            val rawToken = tokenManager.accessToken.first()
            val token = rawToken
                ?.removePrefix("Bearer ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (token == null) {
                Timber.w("Cannot connect WebSocket: No token available")
                return
            }

            reconnectJob?.cancel()
            reconnectJob = null
            intentionalDisconnect = false
            if (resetReconnectAttempts) {
                reconnectAttempts = 0
            }
            webSocket?.cancel()
            webSocket = null
            _connectionState.value = ConnectionState.CONNECTING

            val generation = ++connectGeneration
            val request = Request.Builder()
                .url("$wsUrl?token=$token")
                .build()

            webSocket = okHttpClient.newWebSocket(request, createWebSocketListener(generation))
        }
    }

    override suspend fun reconnect() {
        connectMutex.withLock {
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempts = 0
            intentionalDisconnect = false
            webSocket?.cancel()
            webSocket = null
            pingJob?.cancel()
            ++connectGeneration
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        doConnect(resetReconnectAttempts = true)
    }

    override suspend fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        pingJob?.cancel()
        ++connectGeneration
        _connectionState.value = ConnectionState.DISCONNECTING
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendMessage(message: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Timber.w("Cannot send message: WebSocket not connected")
            return
        }
        webSocket?.send(message)
    }

    override fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    private fun createWebSocketListener(generation: Long) = object : WebSocketListener() {
        private fun isStale(): Boolean = generation != connectGeneration

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (isStale()) return
            Timber.d("WebSocket connected")
            reconnectAttempts = 0
            _connectionState.value = ConnectionState.CONNECTED
            startPingPong()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStale()) return
            Timber.d("WebSocket message received: $text")
            scope.launch {
                parseMessage(text)?.let { _incomingMessages.emit(it) }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (isStale()) return
            Timber.d("WebSocket closing: $code $reason")
            _connectionState.value = ConnectionState.DISCONNECTING
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isStale()) return
            Timber.d("WebSocket closed: $code $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
            pingJob?.cancel()
            if (!intentionalDisconnect && code != 1000) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isStale()) return
            Timber.e(t, "WebSocket failure")
            _connectionState.value = ConnectionState.FAILED
            pingJob?.cancel()
            if (!intentionalDisconnect) {
                scheduleReconnect()
            }
        }
    }

    private fun parseMessage(text: String): WebSocketMessage? {
        return try {
            when {
                text == "pong" -> WebSocketMessage.Pong
                else -> parseJsonMessage(text)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
            WebSocketMessage.Error(e.message ?: "Parse error")
        }
    }

    private fun parseJsonMessage(text: String): WebSocketMessage? {
        val root = json.parseToJsonElement(text).jsonObject
        val typeField = root["type"]?.jsonPrimitive?.content?.lowercase()

        return when {
            typeField == "error" -> {
                val msg = root["message"]?.jsonPrimitive?.content ?: "Server error"
                WebSocketMessage.Error(msg)
            }
            typeField == "notification" -> parseNotificationEnvelope(text, root)
            typeField in NOTIFICATION_TYPES -> {
                WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(text))
            }
            else -> parseAsChatMessage(text, root)
        }
    }

    /** 服务端补发格式：{"type":"notification","data":{...}} */
    private fun parseNotificationEnvelope(text: String, root: JsonObject): WebSocketMessage? {
        val dataElement = root["data"] ?: return decodeRootNotification(text)
        val payload = dataElement.toString()
        val inner = dataElement.jsonObject
        val innerType = inner["type"]?.jsonPrimitive?.content?.lowercase()
        return when {
            inner.containsKey("sender_id") && inner.containsKey("receiver_id") -> {
                WebSocketMessage.ChatMessage(json.decodeFromString<MessageDto>(payload))
            }
            innerType in NOTIFICATION_TYPES || inner.containsKey("title") -> {
                WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(payload))
            }
            else -> decodeRootNotification(text)
        }
    }

    private fun decodeRootNotification(text: String): WebSocketMessage? {
        return try {
            WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(text))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAsChatMessage(text: String, root: JsonObject): WebSocketMessage? {
        if (!root.containsKey("sender_id") || !root.containsKey("receiver_id")) {
            return null
        }
        val message = json.decodeFromString<MessageDto>(text)
        return WebSocketMessage.ChatMessage(message)
    }

    private fun startPingPong() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(PING_INTERVAL_MS)
                webSocket?.send("ping")
            }
        }
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.w("WebSocket reconnect attempts exhausted")
            return
        }
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            reconnectAttempts++
            val delayMs = if (reconnectAttempts <= 5) 0L else RECONNECT_INTERVAL_MS
            if (delayMs > 0) delay(delayMs)
            if (!intentionalDisconnect &&
                _connectionState.value != ConnectionState.CONNECTED &&
                _connectionState.value != ConnectionState.CONNECTING
            ) {
                connect()
            }
        }
    }
}
