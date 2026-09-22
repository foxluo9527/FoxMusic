package com.fox.music.core.network.interceptor

import com.fox.music.core.network.api.AuthApiService
import com.fox.music.core.network.model.RefreshTokenRequest
import com.fox.music.core.network.token.TokenManager
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApi: Lazy<AuthApiService>,
) : Authenticator {

    private val lock = Any()
    private val refreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null

        if (isPublicAuthPath(response.request.url.encodedPath)) {
            Timber.d("401 on public auth endpoint, skip session invalidation")
            return null
        }

        synchronized(lock) {
            val latestToken = tokenManager.peekAccessToken()
            val requestAuth = response.request.header("Authorization")
            val hadLocalSession = !latestToken.isNullOrBlank() ||
                !tokenManager.peekRefreshToken().isNullOrBlank()

            // 未登录访客请求鉴权接口得到 401：不视为「登录失效」
            if (!hadLocalSession && requestAuth.isNullOrBlank()) {
                Timber.d("401 without local session (guest), ignore")
                return null
            }

            if (responseCount(response) >= 2) {
                Timber.w("401 after retry, giving up")
                if (hadLocalSession) {
                    runBlocking { tokenManager.invalidateSession() }
                }
                return null
            }

            if (!latestToken.isNullOrBlank() && latestToken != requestAuth) {
                return response.request.newBuilder()
                    .header("Authorization", latestToken)
                    .build()
            }

            // 单飞刷新：并发 401 只触发一次 refresh
            while (refreshing.get()) {
                try {
                    (lock as Object).wait(50)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
                val afterWait = tokenManager.peekAccessToken()
                if (!afterWait.isNullOrBlank() && afterWait != requestAuth) {
                    return response.request.newBuilder()
                        .header("Authorization", afterWait)
                        .build()
                }
            }

            refreshing.set(true)
            val refreshed = try {
                runBlocking { tryRefresh() }
            } finally {
                refreshing.set(false)
                (lock as Object).notifyAll()
            }

            return if (refreshed != null) {
                response.request.newBuilder()
                    .header("Authorization", refreshed)
                    .build()
            } else {
                if (hadLocalSession || !requestAuth.isNullOrBlank()) {
                    Timber.d("401 refresh failed — clearing session")
                    runBlocking { tokenManager.invalidateSession() }
                } else {
                    Timber.d("401 refresh skipped — no local session")
                }
                null
            }
        }
    }

    private suspend fun tryRefresh(): String? {
        val refresh = tokenManager.peekRefreshToken()
        if (refresh.isNullOrBlank()) return null
        return try {
            val response = authApi.get().refreshToken(RefreshTokenRequest(refresh))
            val data = response.data
            if (response.isSuccess && data != null && data.token.isNotBlank()) {
                tokenManager.saveTokens(data.token, data.refreshToken ?: refresh)
                tokenManager.peekAccessToken()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Token refresh request failed")
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
