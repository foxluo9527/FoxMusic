package com.fox.music.core.datastore

import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManagerImpl @Inject constructor(
    private val dataStore: FoxPreferencesDataStore,
) : TokenManager {

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val accessToken: Flow<String?> = dataStore.accessToken

    override val isLoggedIn: Flow<Boolean> = dataStore.isLoggedIn

    override val sessionExpired = _sessionExpired.asSharedFlow()

    override suspend fun saveAccessToken(token: String) {
        dataStore.saveAccessToken(token)
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        dataStore.saveTokens(accessToken, refreshToken)
    }

    override suspend fun clearTokens() {
        dataStore.clearTokens()
    }

    override suspend fun invalidateSession() {
        dataStore.clearTokens()
        dataStore.clearUserInfo()
        _sessionExpired.emit(Unit)
    }
}
