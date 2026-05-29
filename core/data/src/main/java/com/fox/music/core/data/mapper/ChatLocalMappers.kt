package com.fox.music.core.data.mapper

import com.fox.music.core.database.entity.ConversationEntity
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.user.User
import com.fox.music.core.network.model.ConversationDto
import com.fox.music.core.network.model.MessageDto

fun MessageEntity.toDomainMessage(): Message = Message(
    id = serverId ?: localId.hashCode().toLong(),
    localId = localId,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    type = type.toMessageType(),
    status = status.toMessageStatus(),
    isRecalled = isRecalled,
    createdAt = sentAt,
    readAt = if (isRead) sentAt else null,
    errorMessage = errorMessage,
    localMediaUri = localMediaUri,
    localMediaFileName = localMediaFileName,
    audioDurationMs = audioDurationMs,
)

fun MessageDto.toMessageEntity(conversationId: Long): MessageEntity = MessageEntity(
    localId = "server_$id",
    serverId = id,
    conversationId = conversationId,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    type = type,
    status = status,
    isRecalled = isRecalled,
    isRead = status == "read",
    sentAt = createdAt,
    cachedAt = parseTimestamp(createdAt),
)

fun ConversationEntity.toChatConversation(): ChatConversation {
    val lastMessage = if (lastMessageLocalId != null) {
        Message(
            id = lastMessageLocalId.hashCode().toLong(),
            localId = lastMessageLocalId,
            senderId = 0L,
            receiverId = peerUserId,
            content = lastMessagePreview,
            type = MessageType.TEXT,
            status = lastMessageStatus?.toMessageStatus() ?: MessageStatus.SENT,
            createdAt = lastMessageAt.toString(),
        )
    } else {
        null
    }
    return ChatConversation(
        id = peerUserId,
        user = User(
            id = peerUserId,
            username = peerMark ?: peerNickname ?: "",
            nickname = peerNickname,
            avatar = peerAvatar,
        ),
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        updatedAt = updatedAt.toString(),
    )
}

fun ConversationDto.toConversationEntity(): ConversationEntity {
    val last = lastMessage
    return ConversationEntity(
        peerUserId = user.id,
        peerNickname = user.nickname ?: user.username,
        peerAvatar = user.avatar,
        peerMark = null,
        lastMessageLocalId = last?.let { "server_${it.id}" },
        lastMessagePreview = last?.let { previewForMessage(it.content, it.type) } ?: "",
        lastMessageStatus = last?.status,
        lastMessageAt = parseTimestamp(last?.createdAt),
        unreadCount = unreadCount,
        updatedAt = parseTimestamp(updatedAt ?: last?.createdAt),
    )
}

fun previewForMessage(content: String, type: String): String = when (type.lowercase()) {
    "image" -> "[图片]"
    "audio" -> "[语音]"
    "file" -> if (isVideoUrl(content)) "[视频]" else "[文件]"
    "music" -> "[音乐]"
    else -> content.take(50)
}

fun previewForMessage(message: Message): String = when (message.type) {
    MessageType.IMAGE -> "[图片]"
    MessageType.AUDIO -> "[语音]"
    MessageType.FILE -> if (isVideoUrl(message.content)) "[视频]" else "[文件]"
    MessageType.MUSIC -> "[音乐]"
    MessageType.TEXT -> message.content.take(50)
}

private fun isVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
        lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
}

private fun String.toMessageType(): MessageType = when (lowercase()) {
    "image" -> MessageType.IMAGE
    "audio" -> MessageType.AUDIO
    "file" -> MessageType.FILE
    "music" -> MessageType.MUSIC
    else -> MessageType.TEXT
}

private fun String.toMessageStatus(): MessageStatus = when (lowercase()) {
    "sending" -> MessageStatus.SENDING
    "sent" -> MessageStatus.SENT
    "delivered" -> MessageStatus.DELIVERED
    "read" -> MessageStatus.READ
    "failed" -> MessageStatus.FAILED
    else -> MessageStatus.SENT
}

private fun parseTimestamp(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()
    value.toLongOrNull()?.let { return it }
    return try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
