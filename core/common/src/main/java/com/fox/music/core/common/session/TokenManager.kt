package com.fox.music.core.common.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface TokenManager {
    val accessToken: Flow<String?>
    val refreshToken: Flow<String?>
    val isLoggedIn: Flow<Boolean>

    /** 内存缓存的 access token，供 OkHttp 拦截器同步读取，避免 runBlocking */
    fun peekAccessToken(): String?

    /** 内存缓存的 refresh token，供 Authenticator 同步判断会话是否存在 */
    fun peekRefreshToken(): String?

    /** 会话失效（如 HTTP 401）时发出，用于跳转登录页 */
    val sessionExpired: SharedFlow<Unit>

    suspend fun saveAccessToken(token: String)
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null)
    suspend fun clearTokens()

    /** 清除本地登录态并通知 UI 要求重新登录（不调用服务端 logout） */
    suspend fun invalidateSession()
}
