package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatchingWithParser
import com.fox.music.core.data.mapper.toUser
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.LoginResult
import com.fox.music.core.model.User
import com.fox.music.core.network.api.AuthApiService
import com.fox.music.core.network.model.ForgotPasswordRequest
import com.fox.music.core.network.model.ResetPasswordRequest
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.network.util.ErrorParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApiService,
    private val tokenManager: TokenManager,
    private val dataStore: FoxPreferencesDataStore
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<LoginResult> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
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
    ): Result<LoginResult> = suspendRunCatchingWithParser(ErrorParser::parseError) {
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

    override suspend fun logout(): Result<Unit> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            authApi.logout()
            tokenManager.clearTokens()
            dataStore.clearUserInfo()
            Unit
        }

    override suspend fun refreshToken(): Result<String> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = authApi.refreshToken()
            val data = response.data
            if (response.isSuccess && data != null) {
                tokenManager.saveAccessToken(data.token)
                data.token
            } else throw Exception(response.message)
        }

    override suspend fun getProfile(): Result<User> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
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
    ): Result<User> = suspendRunCatchingWithParser(ErrorParser::parseError) {
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

    override suspend fun forgotPassword(
        email: String
    ): Result<Unit?> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val response = authApi.forgotPassword(
            ForgotPasswordRequest(email = email)
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data
        } else throw Exception(response.message)
    }

    override suspend fun resetPassword(
        code: String,
        email: String,
        newPassword: String
    ): Result<Unit?> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val response = authApi.resetPassword(
            ResetPasswordRequest(email = email, code = code, newPassword = newPassword)
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data
        } else throw Exception(response.message)
    }

}
