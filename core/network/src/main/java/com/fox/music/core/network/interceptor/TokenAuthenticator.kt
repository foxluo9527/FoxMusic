package com.fox.music.core.network.interceptor

import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

class UnauthorizedException(message: String = "登录已失效，请重新登录") : Exception(message)

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        synchronized(lock) {
            val currentToken = runBlocking { tokenManager.accessToken.first() }
            // Check if the request that failed already has the latest token
            val requestToken = response.request.header("Authorization")

            if (currentToken != requestToken && !currentToken.isNullOrBlank()) {
                // Token was already refreshed by another thread
                Timber.d("Token was already refreshed by another thread")
                return response.request.newBuilder()
                    .header("Authorization", currentToken)
                    .build()
            }
            runBlocking { tokenManager.clearTokens() }
            return null
        }
    }
}
