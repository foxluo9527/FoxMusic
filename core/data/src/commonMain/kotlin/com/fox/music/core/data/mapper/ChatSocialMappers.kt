package com.fox.music.core.data.mapper

import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.music.Favorite
import com.fox.music.core.model.music.FavoriteType
import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.chat.SearchedUser
import com.fox.music.core.network.model.ConversationDto
import com.fox.music.core.network.model.FavoriteDto
import com.fox.music.core.network.model.FriendDto
import com.fox.music.core.network.model.FriendRequestDto
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto
import com.fox.music.core.network.model.PostDto
import com.fox.music.core.network.model.PostMediaDto
import com.fox.music.core.network.model.SearchedUserDto

fun FriendDto.toFriend(): Friend = Friend(
    id = id,
    username = username,
    nickname = nickname,
    avatar = avatar,
    signature = signature,
    mark = mark
)

fun FriendRequestDto.toFriendRequest(): FriendRequest = FriendRequest(
    id = id,
    userId = userId,
    createdAt = createdAt,
    message = message,
    nickname = nickname,
    avatar = avatar,
    signature = signature
)

fun SearchedUserDto.toSearchedUser(): SearchedUser = SearchedUser(
    id = id,
    nickname = nickname,
    avatar = avatar,
    signature = signature,
    mark = mark,
    isRequested = isRequested,
    isFriend = isFriend
)

// 帖子相关映射（PostDto.toPost / PostLikerDto.toPostLikerUser / PostMediaDto.toPostMedia /
// PostMedia.toDto）已下沉到 :core:mapper 的 SocialMappers.kt —— 社区版块要迁 iOS，
// :core:data 是 android.library，iOS 拿不到。包名相同，调用点零改动。

fun FavoriteDto.toFavorite(): Favorite = Favorite(
    id = id,
    userId = userId,
    type = when (type.lowercase()) {
        "music" -> FavoriteType.MUSIC
        "video" -> FavoriteType.VIDEO
        "novel" -> FavoriteType.NOVEL
        "post" -> FavoriteType.POST
        "artist" -> FavoriteType.ARTIST
        "album" -> FavoriteType.ALBUM
        "playlist" -> FavoriteType.PLAYLIST
        else -> FavoriteType.MUSIC
    },
    targetId = targetId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun NotificationDto.toNotification(): Notification = Notification(
    id = id,
    type = resolveNotificationType(type, targetType),
    title = title,
    content = content,
    isRead = isRead,
    createdAt = createdAt,
    sender = sender?.toUser(),
    senderId = sender?.id ?: senderId,
    targetId = targetId,
    targetType = targetType
)

private fun resolveNotificationType(type: String, targetType: String?): NotificationType {
    when (type.lowercase()) {
        "system" -> return NotificationType.SYSTEM
        "friend_request" -> return NotificationType.FRIEND_REQUEST
        "comment" -> return NotificationType.COMMENT
        "like" -> return NotificationType.LIKE
        "follow" -> return NotificationType.FOLLOW
        "mention" -> return NotificationType.MENTION
        "music" -> return NotificationType.MUSIC
        "message", "chat", "private_message" -> return NotificationType.MESSAGE
    }
    if (targetType?.lowercase() in CHAT_TARGET_TYPES) {
        return NotificationType.MESSAGE
    }
    return NotificationType.SYSTEM
}

private val CHAT_TARGET_TYPES = setOf("message", "chat", "private_message")

fun MessageDto.toMessage(): Message = Message(
    id = id,
    localId = "server_$id",
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    type = when {
        !voiceUrl.isNullOrBlank() -> MessageType.AUDIO
        type.equals("voice", ignoreCase = true) -> MessageType.AUDIO
        else -> when (type.lowercase()) {
        "text" -> MessageType.TEXT
        "image" -> MessageType.IMAGE
        "audio" -> MessageType.AUDIO
        // ⚠️ 图片/视频在传输层是 type=file + file_type=image/video；
        // 若一律映射成 FILE，气泡会渲染成「文件名」而不是图片预览
        //（Android 本地存的是 "image" 所以没这问题，iOS 全走本映射器）。
        "file" -> if (fileType?.lowercase()?.startsWith("image") == true) {
            MessageType.IMAGE
        } else {
            MessageType.FILE
        }
        "music" -> MessageType.MUSIC
        "share" -> MessageType.SHARE
        else -> MessageType.TEXT
        }
    },
    status = when ((status ?: "sent").lowercase()) {
        "sending" -> MessageStatus.SENDING
        "sent" -> MessageStatus.SENT
        "delivered" -> MessageStatus.DELIVERED
        "read" -> MessageStatus.READ
        "failed" -> MessageStatus.FAILED
        else -> MessageStatus.SENT
    },
    isRecalled = isRecalled,
    createdAt = sentAt,
    readAt = if (isRead) sentAt else readAt,
    localMediaFileName = fileName,
    remoteMediaUrl = voiceUrl ?: fileUrl,
    fileType = fileType,
    audioDurationMs = voiceDuration?.times(1000L),
    shareType = shareType,
    shareId = shareId,
    shareData = shareData?.toDomain(),
    videoUrl = videoUrl,
)

fun ConversationDto.toChatConversation(): ChatConversation = ChatConversation(
    id = id,
    user = user.toUser(),
    lastMessage = lastMessage?.toMessage(),
    unreadCount = unreadCount,
    updatedAt = updatedAt,
    isPinned = false,
)
