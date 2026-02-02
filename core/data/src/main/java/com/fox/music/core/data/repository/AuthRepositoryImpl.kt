package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toUser
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.LoginResult
import com.fox.music.core.model.User
import com.fox.music.core.network.api.AuthApiService
import com.fox.music.core.network.token.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApiService,
    private val tokenManager: TokenManager,
    private val dataStore: FoxPreferencesDataStore
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<LoginResult> =
        suspendRunCatching {
            val response = authApi.login(
                com.fox.music.core.network.model.LoginRequest(
                    username = username,
                    password = password
                )
            )
            val data = response.data
            if (response.isSuccess && data != null) {
                tokenManager.saveTokens(data.token, null)
                dataStore.saveUserInfo(data.user.id.toString(), data.user.username)
                LoginResult(token = data.token, user = data.user.toUser())
            } else throw Exception(response.message)
        }

    override suspend fun register(
        username: String,
        password: String,
        email: String
    ): Result<LoginResult> = suspendRunCatching {
        val response = authApi.register(
            com.fox.music.core.network.model.RegisterRequest(
                username = username,
                password = password,
                email = email
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            tokenManager.saveTokens(data.token, null)
            dataStore.saveUserInfo(data.user.id.toString(), data.user.username)
            LoginResult(token = data.token, user = data.user.toUser())
        } else throw Exception(response.message)
    }

    override suspend fun logout(): Result<Unit> = suspendRunCatching {
        authApi.logout()
        tokenManager.clearTokens()
        dataStore.clearUserInfo()
        Unit
    }

    override suspend fun refreshToken(): Result<String> = suspendRunCatching {
        val response = authApi.refreshToken()
        val data = response.data
        if (response.isSuccess && data != null) {
            tokenManager.saveAccessToken(data.token)
            data.token
        } else throw Exception(response.message)
    }

    override suspend fun getProfile(): Result<User> = suspendRunCatching {
        val response = authApi.getProfile()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toUser()
        } else throw Exception(response.message)
    }

    override suspend fun updateProfile(
        nickname: String?,
        avatar: String?,
        signature: String?,
        email: String?
    ): Result<User> = suspendRunCatching {
        val response = authApi.updateProfile(
            com.fox.music.core.network.model.UpdateProfileRequest(
                nickname = nickname,
                avatar = avatar,
                signature = signature,
                email = email
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toUser()
        } else throw Exception(response.message)
    }
}
