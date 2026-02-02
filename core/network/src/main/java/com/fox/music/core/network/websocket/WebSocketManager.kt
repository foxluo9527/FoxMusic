package com.fox.music.core.network.websocket

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import com.fox.music.core.network.model.MessageDto

interface WebSocketManager {
    val connectionState: Flow<ConnectionState>
    val incomingMessages: SharedFlow<WebSocketMessage>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendMessage(message: String)
    fun isConnected(): Boolean
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
    data class Notification(val type: String, val data: String) : WebSocketMessage()
    data class Error(val error: String) : WebSocketMessage()
    data object Pong : WebSocketMessage()
}
