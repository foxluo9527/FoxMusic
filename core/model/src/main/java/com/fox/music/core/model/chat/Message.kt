package com.fox.music.core.model.chat

import com.fox.music.core.model.user.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long,
    val localId: String? = null,
    @SerialName("sender_id")
    val senderId: Long,
    @SerialName("receiver_id")
    val receiverId: Long,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    @SerialName("is_recalled")
    val isRecalled: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null,
    val errorMessage: String? = null,
    val localMediaUri: String? = null,
    val localMediaFileName: String? = null,
    val remoteMediaUrl: String? = null,
    val fileType: String? = null,
    val uploadedAt: Long? = null,
    val audioDurationMs: Long? = null,
)

@Serializable
enum class MessageType {
    @SerialName("text")
    TEXT,
    @SerialName("image")
    IMAGE,
    @SerialName("audio")
    AUDIO,
    @SerialName("file")
    FILE,
    @SerialName("music")
    MUSIC
}

@Serializable
enum class MessageStatus {
    @SerialName("sending")
    SENDING,
    @SerialName("sent")
    SENT,
    @SerialName("delivered")
    DELIVERED,
    @SerialName("read")
    READ,
    @SerialName("failed")
    FAILED
}

@Serializable
data class ChatConversation(
    val id: Long,
    val user: User,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val isPinned: Boolean = false,
)

data class SearchResultItem(
    val user: User,
    val lastMessage: Message,
    val matchCount: Int,
)
