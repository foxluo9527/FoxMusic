package com.fox.music.core.datastore

import com.fox.music.core.common.session.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManagerImpl @Inject constructor(
    private val dataStore: FoxPreferencesDataStore,
) : TokenManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cachedAccessToken = AtomicReference<String?>(null)
    private val cachedRefreshToken = AtomicReference<String?>(null)
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        // 异步预热，避免阻塞启动线程；首个请求若尚未预热完则由 Authenticator 兜底刷新。
        scope.launch {
            cachedAccessToken.set(dataStore.accessToken.first())
            cachedRefreshToken.set(dataStore.refreshToken.first())
            launch {
                dataStore.accessToken.collect { cachedAccessToken.set(it) }
            }
            launch {
                dataStore.refreshToken.collect { cachedRefreshToken.set(it) }
            }
        }
    }

    override val accessToken: Flow<String?> = dataStore.accessToken

    override val refreshToken: Flow<String?> = dataStore.refreshToken

    override val isLoggedIn: Flow<Boolean> = dataStore.isLoggedIn

    override val sessionExpired = _sessionExpired.asSharedFlow()

    override fun peekAccessToken(): String? = cachedAccessToken.get()

    override fun peekRefreshToken(): String? = cachedRefreshToken.get()

    override suspend fun saveAccessToken(token: String) {
        val normalized = normalizeBearer(token)
        cachedAccessToken.set(normalized)
        dataStore.saveAccessToken(normalized)
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        val normalized = normalizeBearer(accessToken)
        cachedAccessToken.set(normalized)
        cachedRefreshToken.set(refreshToken)
        dataStore.saveTokens(normalized, refreshToken)
    }

    override suspend fun clearTokens() {
        cachedAccessToken.set(null)
        cachedRefreshToken.set(null)
        dataStore.clearTokens()
    }

    override suspend fun invalidateSession() {
        val hadSession = !peekAccessToken().isNullOrBlank() ||
            !peekRefreshToken().isNullOrBlank() ||
            !dataStore.refreshToken.first().isNullOrBlank()
        clearTokens()
        dataStore.clearUserInfo()
        // 从未登录过时不要发 sessionExpired，避免误弹「登录已失效」并跳登录页
        if (hadSession) {
            _sessionExpired.emit(Unit)
        }
    }

    companion object {
        fun normalizeBearer(token: String): String =
            if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }
}
