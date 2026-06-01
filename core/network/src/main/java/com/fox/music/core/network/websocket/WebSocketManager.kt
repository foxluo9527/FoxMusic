package com.fox.music.core.network.websocket

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto

interface WebSocketManager {
    val connectionState: Flow<ConnectionState>
    val incomingMessages: SharedFlow<WebSocketMessage>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendMessage(message: String)
    fun isConnected(): Boolean
    /** 未连接时建立连接，不中断已有连接（后台保活用） */
    suspend fun ensureConnected()
    /** 强制断开后重连（回前台同步补发用） */
    suspend fun reconnect()
}

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    FAILED
}

sealed class WebSocketMessage {
    data class ChatMessage(val message: MessageDto) : WebSocketMessage()
    data class Notification(val notification: NotificationDto) : WebSocketMessage()
    data class Error(val error: String) : WebSocketMessage()
    data object Pong : WebSocketMessage()
}
