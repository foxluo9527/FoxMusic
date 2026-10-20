package com.fox.music.core.network.websocket

import android.content.Context
import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.di.WebSocketClient
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.network.util.isDebuggable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManagerImpl @Inject constructor(
    @WebSocketClient private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
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

    private val wsUrl: String = FoxNetworkConfig.API_BASE_URL
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/')
        .plus("/ws")

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 15
        private const val RECONNECT_BASE_MS = 1_000L
        private const val RECONNECT_MAX_MS = 30_000L
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

            // 服务端只认 query token（见 WebsocketClient.md），不要走 Authorization header
            val token = tokenManager.peekAccessToken()
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
        connectMutex.withLock {
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
            // OkHttp Client 已配置 pingInterval，不再叠加应用层 ping
            pingJob?.cancel()
            pingJob = null
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStale()) return
            if (isDebuggable(context)) {
                Timber.d("WebSocket message received (%d bytes)", text.length)
            }
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

    /** 解析下沉到 commonMain 的 [WebSocketMessageParser]，与 iOS 端共用同一套规则 */
    private fun parseMessage(text: String): WebSocketMessage? {
        val parsed = WebSocketMessageParser.parse(json, text)
        if (parsed is WebSocketMessage.Error) {
            Timber.e("Failed to parse WebSocket message: ${parsed.error}")
        }
        return parsed
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
            val exp = (RECONNECT_BASE_MS * (1L shl (reconnectAttempts - 1).coerceAtMost(5)))
                .coerceAtMost(RECONNECT_MAX_MS)
            val jitter = (0..250).random().toLong()
            delay(exp + jitter)
            if (!intentionalDisconnect &&
                _connectionState.value != ConnectionState.CONNECTED &&
                _connectionState.value != ConnectionState.CONNECTING
            ) {
                connect()
            }
        }
    }
}
