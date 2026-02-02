package com.fox.music.core.data.mapper

import com.fox.music.core.model.ChatConversation
import com.fox.music.core.model.Favorite
import com.fox.music.core.model.FavoriteType
import com.fox.music.core.model.Friend
import com.fox.music.core.model.FriendRequest
import com.fox.music.core.model.Message
import com.fox.music.core.model.MessageStatus
import com.fox.music.core.model.MessageType
import com.fox.music.core.model.Notification
import com.fox.music.core.model.NotificationType
import com.fox.music.core.model.Post
import com.fox.music.core.model.SearchedUser
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
    user = user?.toUser()
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
    type = when (type.lowercase()) {
        "system" -> NotificationType.SYSTEM
        "friend_request" -> NotificationType.FRIEND_REQUEST
        "comment" -> NotificationType.COMMENT
        "like" -> NotificationType.LIKE
        "follow" -> NotificationType.FOLLOW
        "music" -> NotificationType.MUSIC
        else -> NotificationType.SYSTEM
    },
    title = title,
    content = content,
    isRead = isRead,
    createdAt = createdAt,
    sender = sender?.toUser(),
    targetId = targetId,
    targetType = targetType
)

fun MessageDto.toMessage(): Message = Message(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    type = when (type.lowercase()) {
        "text" -> MessageType.TEXT
        "image" -> MessageType.IMAGE
        "audio" -> MessageType.AUDIO
        "file" -> MessageType.FILE
        "music" -> MessageType.MUSIC
        else -> MessageType.TEXT
    },
    status = when (status.lowercase()) {
        "sending" -> MessageStatus.SENDING
        "sent" -> MessageStatus.SENT
        "delivered" -> MessageStatus.DELIVERED
        "read" -> MessageStatus.READ
        "failed" -> MessageStatus.FAILED
        else -> MessageStatus.SENT
    },
    isRecalled = isRecalled,
    createdAt = createdAt,
    readAt = readAt
)

fun ConversationDto.toChatConversation(): ChatConversation = ChatConversation(
    id = id,
    user = user.toUser(),
    lastMessage = lastMessage?.toMessage(),
    unreadCount = unreadCount,
    updatedAt = updatedAt
)
