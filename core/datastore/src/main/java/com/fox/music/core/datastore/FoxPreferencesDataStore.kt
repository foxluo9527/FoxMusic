package com.fox.music.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val PLAY_QUALITY = stringPreferencesKey("play_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val LANGUAGE = stringPreferencesKey("language")
        val DOWNLOAD_ON_WIFI_ONLY = booleanPreferencesKey("download_on_wifi_only")
        val CACHE_MAX_BYTES = longPreferencesKey("cache_max_bytes")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val PLAYBACK_SNAPSHOT = stringPreferencesKey("playback_snapshot")
        val PLAYBACK_CURRENT_INDEX = intPreferencesKey("playback_current_index")
        val PLAYBACK_POSITION_MS = longPreferencesKey("playback_position_ms")
        val PLAYBACK_PLAYING_KEY = stringPreferencesKey("playback_playing_key")
    }

    // Token flows
    val accessToken: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.ACCESS_TOKEN] }
    val isLoggedIn: Flow<Boolean> = accessToken.map { !it.isNullOrBlank() }

    // User info flows
    val userId: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.USER_ID] }
    val username: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.USERNAME] }

    // Settings flows
    val appearanceMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.APPEARANCE_MODE]
            ?: when (prefs[booleanPreferencesKey("dark_mode")]) {
                true -> "dark"
                false -> "light"
                null -> "follow_system"
            }
    }
    val autoPlay: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.AUTO_PLAY] ?: true }
    val playQuality: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.PLAY_QUALITY] ?: "high" }
    val downloadQuality: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.DOWNLOAD_QUALITY] ?: "high" }
    val showLyrics: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.SHOW_LYRICS] ?: true }
    val language: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LANGUAGE] ?: "en" }
    val downloadOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.DOWNLOAD_ON_WIFI_ONLY] ?: true }
    val cacheMaxBytes: Flow<Long> = context.dataStore.data.map {
        it[PreferencesKeys.CACHE_MAX_BYTES] ?: DEFAULT_CACHE_MAX_BYTES
    }

    // Token operations
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACCESS_TOKEN] = accessToken
        }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[PreferencesKeys.ACCESS_TOKEN] = token }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.ACCESS_TOKEN)
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
    suspend fun updateAppearanceMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.APPEARANCE_MODE] = mode }
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

    suspend fun updateCacheMaxBytes(maxBytes: Long) {
        context.dataStore.edit { it[PreferencesKeys.CACHE_MAX_BYTES] = maxBytes }
    }

    suspend fun getCacheMaxBytes(): Long {
        return context.dataStore.data.first()[PreferencesKeys.CACHE_MAX_BYTES] ?: DEFAULT_CACHE_MAX_BYTES
    }

    // Playback state
    suspend fun getRepeatModeName(): String {
        return context.dataStore.data.first()[PreferencesKeys.REPEAT_MODE] ?: RepeatModeDefault
    }

    suspend fun saveRepeatModeName(name: String) {
        context.dataStore.edit { it[PreferencesKeys.REPEAT_MODE] = name }
    }

    suspend fun getPlaybackSnapshotJson(): String? {
        return context.dataStore.data.first()[PreferencesKeys.PLAYBACK_SNAPSHOT]
    }

    suspend fun savePlaybackSnapshotJson(json: String) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_SNAPSHOT] = json }
    }

    suspend fun getPlaybackCurrentIndex(): Int {
        return context.dataStore.data.first()[PreferencesKeys.PLAYBACK_CURRENT_INDEX] ?: 0
    }

    suspend fun savePlaybackCurrentIndex(index: Int) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_CURRENT_INDEX] = index }
    }

    suspend fun getPlaybackPositionMs(): Long {
        return context.dataStore.data.first()[PreferencesKeys.PLAYBACK_POSITION_MS] ?: 0L
    }

    suspend fun savePlaybackPositionMs(positionMs: Long) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_POSITION_MS] = positionMs }
    }

    suspend fun getPlaybackPlayingKey(): String? {
        return context.dataStore.data.first()[PreferencesKeys.PLAYBACK_PLAYING_KEY]
    }

    suspend fun savePlaybackPlayingKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key == null) {
                prefs.remove(PreferencesKeys.PLAYBACK_PLAYING_KEY)
            } else {
                prefs[PreferencesKeys.PLAYBACK_PLAYING_KEY] = key
            }
        }
    }

    suspend fun clearPlaybackSnapshot() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.PLAYBACK_SNAPSHOT)
            prefs.remove(PreferencesKeys.PLAYBACK_CURRENT_INDEX)
            prefs.remove(PreferencesKeys.PLAYBACK_POSITION_MS)
            prefs.remove(PreferencesKeys.PLAYBACK_PLAYING_KEY)
        }
    }

    companion object {
        private const val RepeatModeDefault = "ALL"
        const val DEFAULT_CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024
    }
}
