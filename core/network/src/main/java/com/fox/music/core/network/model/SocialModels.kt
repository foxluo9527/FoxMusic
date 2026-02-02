package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendDto(
    val id: Long,
    val username: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val mark: String? = null
)

@Serializable
data class FriendRequestDto(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null
)

@Serializable
data class SearchedUserDto(
    val id: Long,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val mark: String? = null,
    @SerialName("is_requested")
    val isRequested: Boolean = false,
    @SerialName("is_friend")
    val isFriend: Boolean = false
)

@Serializable
data class FriendRequestBody(
    @SerialName("friend_id")
    val friendId: Long,
    val message: String,
    val mark: String? = null
)

@Serializable
data class AcceptFriendRequest(
    val requestId: Long
)

@Serializable
data class SetRemarkRequest(
    val friendId: Long,
    val remark: String
)

@Serializable
data class PostDto(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    val content: String,
    val images: List<String> = emptyList(),
    val music: MusicDto? = null,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("comment_count")
    val commentCount: Int = 0,
    @SerialName("share_count")
    val shareCount: Int = 0,
    val isLiked: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val user: UserDto? = null
)

@Serializable
data class CreatePostRequest(
    val content: String,
    val images: List<String>? = null,
    @SerialName("music_id")
    val musicId: Long? = null
)

@Serializable
data class FavoriteDto(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    val type: String,
    @SerialName("target_id")
    val targetId: Long,
    val title: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class NotificationDto(
    val id: Long,
    val type: String,
    val title: String,
    val content: String,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: UserDto? = null,
    val targetId: Long? = null,
    val targetType: String? = null
)

@Serializable
data class MarkReadRequest(
    val ids: List<Long>
)

@Serializable
data class DeleteNotificationsRequest(
    val ids: List<Long>
)
