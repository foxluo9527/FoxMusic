package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Friend(
    val id: Long,
    val username: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val mark: String? = null
)

@Serializable
data class FriendRequest(
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
data class SearchedUser(
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
