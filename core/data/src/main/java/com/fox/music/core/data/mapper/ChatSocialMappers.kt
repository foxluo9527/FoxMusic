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

fun PostDto.toPost(): Post = Post(
    id = id,
    userId = userId,
    content = content,
    images = images,
    music = music?.toMusic(),
    likeCount = likeCount,
    commentCount = commentCount,
    shareCount = shareCount,
    isLiked = isLiked,
    createdAt = createdAt,
    updatedAt = updatedAt,
    authorName = authorName,
    authorAvatar = authorAvatar
)

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
        "file" -> MessageType.FILE
        "music" -> MessageType.MUSIC
        else -> MessageType.TEXT
        }
    },
    status = when (resolveStatus().lowercase()) {
        "sending" -> MessageStatus.SENDING
        "sent" -> MessageStatus.SENT
        "delivered" -> MessageStatus.DELIVERED
        "read" -> MessageStatus.READ
        "failed" -> MessageStatus.FAILED
        else -> MessageStatus.SENT
    },
    isRecalled = resolveIsRecalled(),
    createdAt = resolveSentAt(),
    readAt = if (isRead) resolveSentAt() else readAt,
    localMediaFileName = fileName,
    remoteMediaUrl = voiceUrl ?: fileUrl,
    fileType = fileType,
    audioDurationMs = voiceDuration?.times(1000L),
    shareType = shareType,
    shareId = shareId,
    shareData = shareData?.toDomain(),
)

fun ConversationDto.toChatConversation(): ChatConversation = ChatConversation(
    id = id,
    user = user.toUser(),
    lastMessage = lastMessage?.toMessage(),
    unreadCount = unreadCount,
    updatedAt = updatedAt,
    isPinned = false,
)
