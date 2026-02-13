package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *                 "id": 2,
 *                 "title": "测试图片",
 *                 "content": "这是一篇图片测试帖子",
 *                 "author_id": 1,
 *                 "view_count": 0,
 *                 "like_count": 0,
 *                 "collection_count": 0,
 *                 "comment_count": 0,
 *                 "is_pinned": 0,
 *                 "is_featured": 0,
 *                 "created_at": "2025-12-21T14:37:46.000Z",
 *                 "updated_at": "2025-12-21T14:37:46.000Z",
 *                 "author_name": "root",
 *                 "author_avatar": "/uploads/oss/image/1751031665649_ge6yc1.jpg",
 *                 "tags": [],
 *                 "images": [],
 *                 "is_liked": false,
 *                 "is_favorited": false
 */
@Serializable
data class Post(
    val id: Long,
    @SerialName("author_id")
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
    @SerialName("is_liked")
    val isLiked: Boolean = false,
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
