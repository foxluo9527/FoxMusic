package com.fox.music.core.network.interceptor

import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null

        synchronized(lock) {
            if (responseCount(response) >= 2) {
                Timber.w("401 after retry, giving up")
                return null
            }
            Timber.d("401 Unauthorized — clearing session, require re-login")
            runBlocking { tokenManager.invalidateSession() }
            return null
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
