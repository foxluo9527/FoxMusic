package com.fox.music.core.model.user

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

/** 当前用户是否为管理员（`role` 为 admin，不区分大小写） */
val User.isAdmin: Boolean
    get() = role?.contains(UserRole.ADMIN, ignoreCase = true) == true

object UserRole {
    const val ADMIN = "admin"
}

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

val UserProfile.isAdmin: Boolean
    get() = role.equals(UserRole.ADMIN, ignoreCase = true)
