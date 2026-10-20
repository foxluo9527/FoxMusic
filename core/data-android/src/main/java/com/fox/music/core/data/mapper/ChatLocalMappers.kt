package com.fox.music.core.data.mapper

import com.fox.music.core.database.entity.ConversationEntity
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.chat.ShareData
import com.fox.music.core.model.user.User
import com.fox.music.core.network.model.ConversationDto
import com.fox.music.core.network.model.MessageDto
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun Message.toIncomingMessageEntity(
    conversationId: Long,
    ownerUserId: Long,
): MessageEntity = MessageEntity(
    localId = localId ?: "server_$id",
    ownerUserId = ownerUserId,
    serverId = id,
    conversationId = conversationId,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    type = type.toEntityType(),
    status = status.toEntityStatus(),
    localMediaFileName = localMediaFileName,
    remoteMediaUrl = remoteMediaUrl,
    fileType = fileType,
    audioDurationMs = audioDurationMs,
    isRecalled = isRecalled,
    isRead = status == MessageStatus.READ || readAt != null,
    sentAt = createdAt,
    cachedAt = parseTimestamp(createdAt),
    shareType = shareType,
    shareId = shareId,
    shareDataJson = shareData?.let { json.encodeToString(ShareData.serializer(), it) },
    localVideoUri = localVideoUri,
    remoteVideoUrl = videoUrl,
)

private fun MessageType.toEntityType(): String = when (this) {
    MessageType.TEXT -> "text"
    MessageType.IMAGE -> "image"
    MessageType.AUDIO -> "voice"
    MessageType.FILE -> "file"
    MessageType.MUSIC -> "music"
    MessageType.SHARE -> "share"
}

private fun MessageStatus.toEntityStatus(): String = when (this) {
    MessageStatus.SENDING -> "sending"
    MessageStatus.SENT -> "sent"
    MessageStatus.DELIVERED -> "delivered"
    MessageStatus.READ -> "read"
    MessageStatus.FAILED -> "failed"
}

fun MessageEntity.toDomainMessage(): Message = Message(
    id = serverId ?: localId.hashCode().toLong(),
    localId = localId,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    type = type.toMessageType(),
    status = status.toMessageStatus(),
    isRecalled = isRecalled,
    createdAt = sentAt?.takeIf { it.isNotBlank() } ?: cachedAt.toString(),
    readAt = if (isRead) sentAt else null,
    errorMessage = errorMessage,
    localMediaUri = localMediaUri,
    localVideoUri = localVideoUri,
    localMediaFileName = localMediaFileName,
    remoteMediaUrl = remoteMediaUrl,
    fileType = fileType,
    uploadedAt = uploadedAt,
    audioDurationMs = audioDurationMs,
    shareType = shareType,
    shareId = shareId,
    shareData = shareDataJson?.let {
        runCatching { json.decodeFromString(ShareData.serializer(), it) }.getOrNull()
    },
    videoUrl = remoteVideoUrl,
)

// resolveStatus / resolveSentAt / resolveIsRecalled 已下沉到 :core:data 的
// commonMain（ChatPreviewMappers.kt）—— ChatSocialMappers 也要用它们。

fun MessageDto.toMessageEntity(conversationId: Long, ownerUserId: Long): MessageEntity {
    val timestamp = resolveSentAt()
    val resolvedType = when {
        !voiceUrl.isNullOrBlank() -> "voice"
        type.equals("voice", ignoreCase = true) -> "voice"
        type.equals("share", ignoreCase = true) -> "share"
        // 传输层 type=file + file_type=image 实际是图片：本地存 "image"，
        // 否则 toDomainMessage 映射成 FILE，气泡渲染成文件名而非图片预览
        type.equals("file", ignoreCase = true) &&
            fileType?.lowercase()?.startsWith("image") == true -> "image"
        else -> type
    }
    return MessageEntity(
        localId = "server_$id",
        ownerUserId = ownerUserId,
        serverId = id,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        type = resolvedType,
        status = resolveStatus(),
        localMediaFileName = fileName,
        remoteMediaUrl = voiceUrl ?: fileUrl,
        fileType = fileType,
        audioDurationMs = voiceDuration?.times(1000L),
        isRecalled = resolveIsRecalled(),
        isRead = isRead || resolveStatus() == "read",
        sentAt = timestamp,
        cachedAt = parseTimestamp(timestamp),
        shareType = shareType,
        shareId = shareId,
        shareDataJson = shareData?.let { json.encodeToString(ShareData.serializer(), it.toDomain()) },
        localVideoUri = null,
        remoteVideoUrl = videoUrl,
    )
}

fun ConversationEntity.toChatConversation(): ChatConversation {
    val hasPreview = lastMessageLocalId != null || lastMessagePreview.isNotBlank()
    val lastMessage = if (hasPreview) {
        Message(
            id = (lastMessageLocalId ?: "preview_$peerUserId").hashCode().toLong(),
            localId = lastMessageLocalId ?: "preview_$peerUserId",
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
            // 有备注时优先用备注作为展示名（displayName 优先 nickname）
            nickname = peerMark?.takeIf { it.isNotBlank() } ?: peerNickname,
            avatar = peerAvatar,
        ),
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        updatedAt = updatedAt.toString(),
        isPinned = isPinned,
    )
}

fun ConversationDto.toConversationEntity(ownerUserId: Long): ConversationEntity {
    val last = lastMessage
    return ConversationEntity(
        ownerUserId = ownerUserId,
        peerUserId = user.id,
        peerNickname = user.nickname ?: user.username,
        peerAvatar = user.avatar,
        peerMark = null,
        lastMessageLocalId = last?.let { "server_${it.id}" },
        lastMessagePreview = last?.let { previewForMessage(it.content, it.type) } ?: "",
        lastMessageStatus = last?.resolveStatus(),
        lastMessageAt = parseTimestamp(last?.resolveSentAt()),
        unreadCount = unreadCount,
        updatedAt = parseTimestamp(updatedAt ?: last?.createdAt),
    )
}

// previewForMessage(...) ×2 / isVideoUrl / isImageFileName 已下沉到
// :core:data 的 commonMain（ChatPreviewMappers.kt）—— 实时协调器要用它们生成通知摘要。
// 包名不变，调用点零改动。

private fun String.toMessageType(): MessageType = when (lowercase()) {
    "image" -> MessageType.IMAGE
    "audio", "voice" -> MessageType.AUDIO
    "video", "file" -> MessageType.FILE
    "music" -> MessageType.MUSIC
    "share" -> MessageType.SHARE
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

// ShareDataDto.toDomain() 已随 ChatPreviewMappers.kt 下沉到 :core:data 的 commonMain。
