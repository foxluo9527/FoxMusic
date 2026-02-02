package com.fox.music.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fox_preferences")

@Singleton
class FoxPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val PLAY_QUALITY = stringPreferencesKey("play_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val LANGUAGE = stringPreferencesKey("language")
        val DOWNLOAD_ON_WIFI_ONLY = booleanPreferencesKey("download_on_wifi_only")
    }

    // Token flows
    val accessToken: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.REFRESH_TOKEN] }
    val isLoggedIn: Flow<Boolean> = accessToken.map { !it.isNullOrBlank() }

    // User info flows
    val userId: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.USER_ID] }
    val username: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.USERNAME] }

    // Settings flows
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: false }
    val autoPlay: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.AUTO_PLAY] ?: true }
    val playQuality: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.PLAY_QUALITY] ?: "high" }
    val downloadQuality: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.DOWNLOAD_QUALITY] ?: "high" }
    val showLyrics: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.SHOW_LYRICS] ?: true }
    val language: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LANGUAGE] ?: "en" }
    val downloadOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.DOWNLOAD_ON_WIFI_ONLY] ?: true }

    // Token operations
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[PreferencesKeys.REFRESH_TOKEN] = it }
        }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[PreferencesKeys.ACCESS_TOKEN] = token }
    }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { it[PreferencesKeys.REFRESH_TOKEN] = token }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.ACCESS_TOKEN)
            prefs.remove(PreferencesKeys.REFRESH_TOKEN)
        }
    }

    // User info operations
    suspend fun saveUserInfo(userId: String, username: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USER_ID] = userId
            prefs[PreferencesKeys.USERNAME] = username
        }
    }

    suspend fun clearUserInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.USER_ID)
            prefs.remove(PreferencesKeys.USERNAME)
        }
    }

    // Settings operations
    suspend fun updateDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DARK_MODE] = enabled }
    }

    suspend fun updateAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_PLAY] = enabled }
    }

    suspend fun updatePlayQuality(quality: String) {
        context.dataStore.edit { it[PreferencesKeys.PLAY_QUALITY] = quality }
    }

    suspend fun updateDownloadQuality(quality: String) {
        context.dataStore.edit { it[PreferencesKeys.DOWNLOAD_QUALITY] = quality }
    }

    suspend fun updateShowLyrics(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_LYRICS] = enabled }
    }

    suspend fun updateLanguage(language: String) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language }
    }

    suspend fun updateDownloadOnWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DOWNLOAD_ON_WIFI_ONLY] = enabled }
    }
}
