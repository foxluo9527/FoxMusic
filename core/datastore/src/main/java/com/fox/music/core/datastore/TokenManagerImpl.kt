package com.fox.music.core.datastore

import com.fox.music.core.network.token.TokenManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManagerImpl @Inject constructor(
    private val dataStore: FoxPreferencesDataStore,
) : TokenManager {

    override val accessToken: Flow<String?> = dataStore.accessToken

    override val refreshToken: Flow<String?> = dataStore.refreshToken

    override val isLoggedIn: Flow<Boolean> = dataStore.isLoggedIn

    override suspend fun saveAccessToken(token: String) {
        dataStore.saveAccessToken(token)
    }

    override suspend fun saveRefreshToken(token: String) {
        dataStore.saveRefreshToken(token)
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        dataStore.saveTokens(accessToken, refreshToken)
    }

    override suspend fun clearTokens() {
        dataStore.clearTokens()
    }
}
