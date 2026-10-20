package com.fox.music.core.network.websocket

import com.fox.music.core.common.util.FoxLog
import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.token.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * iOS 侧 WebSocket：Ktor Darwin 引擎（底层 NSURLSession）。
 *
 * 连接/重连/退避语义与 Android 的 OkHttp 版 [com.fox.music.core.network.websocket.WebSocketManagerImpl]
 * 刻意保持一致，报文解析则共用 commonMain 的 [WebSocketMessageParser]。
 *
 * iOS 平台限制（与 Android 最大的差别）：App 进入后台后会被系统挂起，
 * socket 随即被回收，因此**后台期间收不到任何消息**。这不是 bug，平台的
 * 唯一后台推送通道是 APNs。本项目明确不做 APNs，改为「回前台重连 + 补拉未读」
 * 兜底，见 RealtimeMessageCoordinator.onAppForeground()。
 */
class WebSocketManagerIos(
    private val tokenManager: TokenManager,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    },
) : WebSocketManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = HttpClient(Darwin) {
        install(WebSockets) {
            pingIntervalMillis = PING_INTERVAL_MS
        }
    }

    private var session: io.ktor.websocket.WebSocketSession? = null
    private var receiveJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var intentionalDisconnect = false
    private val connectMutex = Mutex()
    /** 每次建连递增，用于丢弃旧连接的回调 */
    private var connectGeneration = 0L

    private val wsUrl: String = FoxNetworkConfig.API_BASE_URL
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/')
        .plus("/ws")

    companion object {
        private const val TAG = "WebSocketIos"
        private const val MAX_RECONNECT_ATTEMPTS = 15
        private const val RECONNECT_BASE_MS = 1_000L
        private const val RECONNECT_MAX_MS = 30_000L
        private const val PING_INTERVAL_MS = 15_000L
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
                FoxLog.w(TAG, "Cannot connect WebSocket: No token available")
                return
            }

            reconnectJob?.cancel()
            reconnectJob = null
            intentionalDisconnect = false
            if (resetReconnectAttempts) {
                reconnectAttempts = 0
            }
            closeSessionQuietly()
            _connectionState.value = ConnectionState.CONNECTING

            val generation = ++connectGeneration
            val url = "$wsUrl?token=$token"

            try {
                val newSession = client.webSocketSession(urlString = url)
                session = newSession
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.CONNECTED
                FoxLog.i(TAG, "WebSocket connected")
                startReceiving(newSession, generation)
            } catch (e: Exception) {
                FoxLog.e(TAG, "WebSocket connect failed", e)
                _connectionState.value = ConnectionState.FAILED
                if (!intentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun startReceiving(activeSession: io.ktor.websocket.WebSocketSession, generation: Long) {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            try {
                for (frame in activeSession.incoming) {
                    if (generation != connectGeneration) return@launch
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    WebSocketMessageParser.parse(json, text)?.let { _incomingMessages.emit(it) }
                }
                // 正常走到这里表示对端关闭了 channel
                if (generation != connectGeneration) return@launch
                _connectionState.value = ConnectionState.DISCONNECTED
                if (!intentionalDisconnect) {
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                if (generation != connectGeneration) return@launch
                FoxLog.e(TAG, "WebSocket receive failed", e)
                _connectionState.value = ConnectionState.FAILED
                if (!intentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    override suspend fun reconnect() {
        connectMutex.withLock {
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempts = 0
            intentionalDisconnect = false
            closeSessionQuietly()
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        doConnect(resetReconnectAttempts = true)
    }

    override suspend fun disconnect() {
        connectMutex.withLock {
            intentionalDisconnect = true
            reconnectJob?.cancel()
            reconnectJob = null
            receiveJob?.cancel()
            receiveJob = null
            ++connectGeneration
            _connectionState.value = ConnectionState.DISCONNECTING
            closeSessionQuietly()
            reconnectAttempts = 0
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun sendMessage(message: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            FoxLog.w(TAG, "Cannot send message: WebSocket not connected")
            return
        }
        try {
            session?.send(Frame.Text(message))
        } catch (e: Exception) {
            FoxLog.e(TAG, "Failed to send message", e)
        }
    }

    override fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    private suspend fun closeSessionQuietly() {
        val current = session ?: return
        session = null
        try {
            current.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            // 已经断开时 close 会抛，忽略即可
        }
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            FoxLog.w(TAG, "WebSocket reconnect attempts exhausted")
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
