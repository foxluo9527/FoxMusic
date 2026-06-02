package com.fox.music.core.data.mapper

import com.fox.music.core.database.entity.ConversationEntity
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.chat.ShareData
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.user.User
import com.fox.music.core.network.model.ConversationDto
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.ShareDataDto
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun Message.toIncomingMessageEntity(conversationId: Long): MessageEntity = MessageEntity(
    localId = localId ?: "server_$id",
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
)

fun MessageDto.resolveStatus(): String =
    status?.takeIf { it.isNotBlank() } ?: if (isRead) "read" else "sent"

fun MessageDto.resolveSentAt(): String? = sentAt ?: createdAt

fun MessageDto.resolveIsRecalled(): Boolean = isRecalled || isDeleted

fun MessageDto.toMessageEntity(conversationId: Long): MessageEntity {
    val timestamp = resolveSentAt()
    val resolvedType = when {
        !voiceUrl.isNullOrBlank() -> "voice"
        type.equals("voice", ignoreCase = true) -> "voice"
        type.equals("share", ignoreCase = true) -> "share"
        else -> type
    }
    return MessageEntity(
        localId = "server_$id",
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
            nickname = peerNickname,
            avatar = peerAvatar,
        ),
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        updatedAt = updatedAt.toString(),
        isPinned = isPinned,
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
        lastMessageStatus = last?.resolveStatus(),
        lastMessageAt = parseTimestamp(last?.resolveSentAt()),
        unreadCount = unreadCount,
        updatedAt = parseTimestamp(updatedAt ?: last?.createdAt),
    )
}

fun previewForMessage(content: String, type: String): String = when (type.lowercase()) {
    "image" -> "[图片]"
    "audio", "voice" -> "[语音]"
    "video" -> "[视频]"
    "file" -> when {
        isVideoUrl(content) -> "[视频]"
        isImageFileName(content) -> "[图片]"
        else -> "[文件]"
    }
    "music" -> "[音乐]"
    "share" -> "[分享]"
    else -> content.take(50)
}

fun previewForMessage(message: Message): String = when (message.type) {
    MessageType.IMAGE -> "[图片]"
    MessageType.AUDIO -> "[语音]"
    MessageType.FILE -> when {
        isVideoUrl(message.content) || message.fileType?.equals("video", ignoreCase = true) == true -> "[视频]"
        isImageFileName(message.content) || message.fileType?.equals("image", ignoreCase = true) == true -> "[图片]"
        else -> "[文件]"
    }
    MessageType.MUSIC -> "[音乐]"
    MessageType.SHARE -> "[分享]"
    MessageType.TEXT -> message.content.take(50)
}

private fun isVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
        lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
}

private fun isImageFileName(value: String): Boolean {
    val name = if (value.startsWith("[file]", ignoreCase = true)) {
        value.removePrefix("[file]")
    } else {
        value.substringAfterLast('/')
    }.lowercase()
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp")
}

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

/** 将服务端 share_data DTO 转为领域模型 */
fun ShareDataDto.toDomain(): ShareData = ShareData(
    id = id,
    title = title,
    name = name,
    coverImage = coverImage,
    avatar = avatar,
    description = description,
    url = url,
    duration = duration,
    trackCount = trackCount,
    playCount = playCount,
    favoriteCount = favoriteCount,
    lyrics = lyrics,
    lyricsTrans = lyricsTrans,
    lyricsUrl = lyricsUrl,
    artists = artists.map { dto ->
        Artist(
            id = dto.id,
            name = dto.name,
            avatar = dto.avatar,
            coverImage = dto.coverImage,
        )
    },
)
