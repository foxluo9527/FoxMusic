package com.fox.music.core.network.model

import com.fox.music.core.network.util.FlexibleBooleanSerializer
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
data class PostLikerDto(
    val id: Long,
    val nickname: String? = null,
    val avatar: String? = null,
)

@Serializable
data class PostMediaDto(
    val type: String = "image",
    val url: String,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
)

@Serializable
data class PostDto(
    val id: Long,
    @SerialName("author_id")
    val userId: Long,
    val title: String? = null,
    val content: String,
    val images: List<String> = emptyList(),
    val media: List<PostMediaDto> = emptyList(),
    /** 兼容字段：标签名列表 */
    val tags: List<String> = emptyList(),
    /** 带 id 的标签，优先用于复用 */
    @SerialName("tag_items")
    val tagItems: List<TagDto> = emptyList(),
    val music: MusicDto? = null,
    @SerialName("music_id")
    val musicId: Long? = null,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("comment_count")
    val commentCount: Int = 0,
    @SerialName("share_count")
    val shareCount: Int = 0,
    @SerialName("collection_count")
    val collectionCount: Int = 0,
    @SerialName("is_liked")
    val isLiked: Boolean = false,
    @SerialName("is_favorited")
    val isFavorited: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("author_name")
    val authorName:String?,
    @SerialName("author_avatar")
    val authorAvatar:String?,
)

@Serializable
data class CreatePostRequest(
    val title: String,
    val content: String,
    val images: List<String>? = null,
    val media: List<PostMediaDto>? = null,
    val tags: List<String>? = null,
    @SerialName("tag_ids")
    val tagIds: List<Long>? = null,
    @SerialName("music_id")
    val musicId: Long? = null,
    val status: String = "published",
)

@Serializable
data class UpdatePostRequest(
    val title: String? = null,
    val content: String? = null,
    val images: List<String>? = null,
    val media: List<PostMediaDto>? = null,
    val tags: List<String>? = null,
    @SerialName("tag_ids")
    val tagIds: List<Long>? = null,
    @SerialName("music_id")
    val musicId: Long? = null,
    val status: String? = null,
)

@Serializable
data class SocialPostCommentRequest(
    val content: String,
    @SerialName("parent_id")
    val parentId: Long? = null,
    val images: List<String>? = null,
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
    @SerialName("user_id")
    val userId: Long? = null,
    val type: String,
    val title: String = "",
    val content: String = "",
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: UserDto? = null,
    @SerialName("sender_id")
    val senderId: Long? = null,
    @SerialName("target_id")
    val targetId: Long? = null,
    @SerialName("target_type")
    val targetType: String? = null,
)

@Serializable
data class MarkReadRequest(
    val ids: List<Long>
)

@Serializable
data class DeleteNotificationsRequest(
    val ids: List<Long>
)
