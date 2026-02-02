package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.User

data class LoginResult(
    val token: String,
    val user: User
)

interface AuthRepository {

    suspend fun login(username: String, password: String): Result<LoginResult>

    suspend fun register(username: String, password: String, email: String): Result<LoginResult>

    suspend fun logout(): Result<Unit>

    suspend fun refreshToken(): Result<String>

    suspend fun getProfile(): Result<User>

    suspend fun updateProfile(
        nickname: String? = null,
        avatar: String? = null,
        signature: String? = null,
        email: String? = null
    ): Result<User>
}
