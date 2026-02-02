package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    val content: String,
    val images: List<String> = emptyList(),
    val music: Music? = null,
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
    val user: User? = null
)

@Serializable
data class Comment(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    val content: String,
    @SerialName("parent_id")
    val parentId: Long? = null,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("reply_count")
    val replyCount: Int = 0,
    val isLiked: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    val user: User? = null,
    val replies: List<Comment> = emptyList()
)
