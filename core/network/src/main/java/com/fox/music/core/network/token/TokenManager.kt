package com.fox.music.core.network.token

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface TokenManager {
    val accessToken: Flow<String?>
    val isLoggedIn: Flow<Boolean>

    /** 会话失效（如 HTTP 401）时发出，用于跳转登录页 */
    val sessionExpired: SharedFlow<Unit>

    suspend fun saveAccessToken(token: String)
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null)
    suspend fun clearTokens()

    /** 清除本地登录态并通知 UI 要求重新登录（不调用服务端 logout） */
    suspend fun invalidateSession()
}
