package com.fox.music.core.network.interceptor

import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for login/register endpoints
        val path = originalRequest.url.encodedPath
        if (path.contains("login") || path.contains("register") ||
            path.contains("forgot-password") || path.contains("reset-password")) {
            return chain.proceed(originalRequest)
        }

        val token = runBlocking { tokenManager.accessToken.first() }

        val request = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", token)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
