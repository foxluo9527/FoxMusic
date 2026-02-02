package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Auth Requests
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String
)

@Serializable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val email: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

// Auth Responses
@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto
)

@Serializable
data class UserDto(
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
