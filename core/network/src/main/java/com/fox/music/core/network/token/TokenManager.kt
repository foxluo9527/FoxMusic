package com.fox.music.core.network.token

import kotlinx.coroutines.flow.Flow

interface TokenManager {
    val accessToken: Flow<String?>
    val refreshToken: Flow<String?>
    val isLoggedIn: Flow<Boolean>

    suspend fun saveAccessToken(token: String)
    suspend fun saveRefreshToken(token: String)
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null)
    suspend fun clearTokens()
}
