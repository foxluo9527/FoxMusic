package com.fox.music.core.network.interceptor

import com.fox.music.core.network.api.AuthApiService
import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiServiceProvider: Provider<AuthApiService>
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        synchronized(lock) {
            val currentToken = runBlocking { tokenManager.accessToken.first() }
            val currentRefreshToken = runBlocking { tokenManager.refreshToken.first() }

            // Check if the request that failed already has the latest token
            val requestToken = response.request.header("Authorization")

            if (currentToken != requestToken && !currentToken.isNullOrBlank()) {
                // Token was already refreshed by another thread
                return response.request.newBuilder()
                    .header("Authorization", currentToken)
                    .build()
            }

            // Check if there are no cached tokens
            if (currentToken.isNullOrBlank() && currentRefreshToken.isNullOrBlank()) {
                // No cached tokens, let user re-login
                return null
            }

            // Try to refresh the token
            return try {
                val refreshResponse = runBlocking {
                    authApiServiceProvider.get().refreshToken()
                }

                if (refreshResponse.isSuccess && refreshResponse.data != null) {
                    val newToken = refreshResponse.data.token
                    runBlocking { tokenManager.saveAccessToken(newToken) }

                    response.request.newBuilder()
                        .header("Authorization", newToken)
                        .build()
                } else {
                    // Refresh failed, clear tokens and let user re-login
                    runBlocking { tokenManager.clearTokens() }
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Token refresh failed")
                runBlocking { tokenManager.clearTokens() }
                null
            }
        }
    }
}
