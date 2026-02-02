package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: Long,
    @SerialName("sender_id")
    val senderId: Long,
    @SerialName("receiver_id")
    val receiverId: Long,
    val content: String,
    val type: String = "text",
    val status: String = "sent",
    @SerialName("is_recalled")
    val isRecalled: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null
)

@Serializable
data class ConversationDto(
    val id: Long,
    val user: UserDto,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class SendMessageRequest(
    val receiverId: Long,
    val content: String,
    val type: String = "text"
)

@Serializable
data class MarkChatReadRequest(
    val targetId: Long
)
