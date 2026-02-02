package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val username: String,
    val email: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val role: String? = null,
    val status: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("last_login")
    val lastLogin: String? = null
)

@Serializable
data class UserProfile(
    val id: Long,
    val username: String,
    val email: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val role: String? = null,
    val status: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("last_login")
    val lastLogin: String? = null,
    val friendCount: Int = 0,
    val playlistCount: Int = 0,
    val favoriteCount: Int = 0
)
