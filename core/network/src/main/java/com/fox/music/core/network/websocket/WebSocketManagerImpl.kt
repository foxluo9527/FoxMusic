package com.fox.music.core.network.websocket

import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManagerImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
    private val json: Json
) : WebSocketManager {

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(
        replay = 0,
        extraBufferCapacity = 64
    )
    override val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private val wsUrl = "ws://39.106.30.151:9000/ws"

    override suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING
        ) {
            return
        }

        val token = tokenManager.accessToken.first()
        if (token.isNullOrBlank()) {
            Timber.w("Cannot connect WebSocket: No token available")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url("$wsUrl?token=$token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
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

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("WebSocket connected")
            _connectionState.value = ConnectionState.CONNECTED
            startPingPong()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Timber.d("WebSocket message received: $text")
            scope.launch {
                parseMessage(text)?.let { _incomingMessages.emit(it) }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closing: $code $reason")
            _connectionState.value = ConnectionState.DISCONNECTING
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closed: $code $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
            pingJob?.cancel()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "WebSocket failure")
            _connectionState.value = ConnectionState.FAILED
            pingJob?.cancel()
            scheduleReconnect()
        }
    }

    private fun parseMessage(text: String): WebSocketMessage? {
        return try {
            when {
                text == "pong" -> WebSocketMessage.Pong
                text.contains("\"type\":\"chat\"") -> {
                    val message = json.decodeFromString<MessageDto>(text)
                    WebSocketMessage.ChatMessage(message)
                }
                text.contains("\"type\":\"notification\"") -> {
                    WebSocketMessage.Notification("notification", text)
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
            WebSocketMessage.Error(e.message ?: "Parse error")
        }
    }

    private fun startPingPong() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(30_000) // 30 seconds
                webSocket?.send("ping")
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000) // 5 seconds
            if (_connectionState.value == ConnectionState.FAILED ||
                _connectionState.value == ConnectionState.DISCONNECTED
            ) {
                connect()
            }
        }
    }
}
