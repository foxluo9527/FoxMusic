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
import com.fox.music.core.datastore.security.TokenCipher
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.NotificationPreferenceSettings
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
    private val tokenCipher: TokenCipher,
) {

    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val PLAY_QUALITY = stringPreferencesKey("play_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val NOTIFY_COMMENT = booleanPreferencesKey("notify_comment")
        val NOTIFY_LIKE = booleanPreferencesKey("notify_like")
        val NOTIFY_FOLLOW = booleanPreferencesKey("notify_follow")
        val NOTIFY_MENTION = booleanPreferencesKey("notify_mention")
        val NOTIFY_MESSAGE = booleanPreferencesKey("notify_message")
        val NOTIFY_FRIEND_REQUEST = booleanPreferencesKey("notify_friend_request")
        val NOTIFY_SYSTEM = booleanPreferencesKey("notify_system")
        val NOTIFY_MUSIC = booleanPreferencesKey("notify_music")
        val LANGUAGE = stringPreferencesKey("language")
        val DOWNLOAD_ON_WIFI_ONLY = booleanPreferencesKey("download_on_wifi_only")
        val CACHE_MAX_BYTES = longPreferencesKey("cache_max_bytes")
        val THEME_PRESET_ID = stringPreferencesKey("theme_preset_id")
        val CUSTOM_THEME_SEED = longPreferencesKey("custom_theme_seed")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val LOCK_SCREEN_LYRIC_ENABLED = booleanPreferencesKey("lock_screen_lyric_enabled")
        val CAR_LYRIC_ENABLED = booleanPreferencesKey("car_lyric_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val PLAYBACK_SNAPSHOT = stringPreferencesKey("playback_snapshot")
        val PLAYBACK_CURRENT_INDEX = intPreferencesKey("playback_current_index")
        val PLAYBACK_POSITION_MS = longPreferencesKey("playback_position_ms")
        val PLAYBACK_PLAYING_KEY = stringPreferencesKey("playback_playing_key")
        val PLAYBACK_PLAYLIST_SOURCE = stringPreferencesKey("playback_playlist_source")
        val PLAZA_LAYOUT_MODE = stringPreferencesKey("plaza_layout_mode")
        /** 音流「听歌模式」：记住上次选择的场景 mood，冷启动沿用 */
        val STREAM_MOOD = stringPreferencesKey("stream_mood")
        val STREAM_SESSION = stringPreferencesKey("stream_session")
    }

    // Token flows（DataStore 中为密文，对外始终解密）
    val accessToken: Flow<String?> = context.dataStore.data.map {
        tokenCipher.decrypt(it[PreferencesKeys.ACCESS_TOKEN])
    }
    val refreshToken: Flow<String?> = context.dataStore.data.map {
        tokenCipher.decrypt(it[PreferencesKeys.REFRESH_TOKEN])
    }
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
    val notificationSettings: Flow<NotificationPreferenceSettings> = context.dataStore.data.map { prefs ->
        NotificationPreferenceSettings(
            commentEnabled = prefs[PreferencesKeys.NOTIFY_COMMENT] ?: true,
            likeEnabled = prefs[PreferencesKeys.NOTIFY_LIKE] ?: true,
            followEnabled = prefs[PreferencesKeys.NOTIFY_FOLLOW] ?: true,
            mentionEnabled = prefs[PreferencesKeys.NOTIFY_MENTION] ?: true,
            messageEnabled = prefs[PreferencesKeys.NOTIFY_MESSAGE]
                ?: prefs[booleanPreferencesKey("in_app_notifications")]
                ?: true,
            friendRequestEnabled = prefs[PreferencesKeys.NOTIFY_FRIEND_REQUEST] ?: true,
            systemEnabled = prefs[PreferencesKeys.NOTIFY_SYSTEM] ?: true,
            musicEnabled = prefs[PreferencesKeys.NOTIFY_MUSIC] ?: true,
        )
    }
    val language: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LANGUAGE] ?: "en" }
    val downloadOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.DOWNLOAD_ON_WIFI_ONLY] ?: true }
    val cacheMaxBytes: Flow<Long> = context.dataStore.data.map {
        it[PreferencesKeys.CACHE_MAX_BYTES] ?: DEFAULT_CACHE_MAX_BYTES
    }
    val themePresetId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.THEME_PRESET_ID]
            ?: if (prefs[PreferencesKeys.DYNAMIC_COLOR] == true) {
                AppThemeIds.VINYL_NIGHT
            } else {
                AppThemeIds.DEFAULT
            }
    }
    val customThemeSeed: Flow<Long> = context.dataStore.data.map {
        it[PreferencesKeys.CUSTOM_THEME_SEED] ?: 0xFFE8A838
    }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.DYNAMIC_COLOR] ?: false
    }
    val desktopLyricEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DESKTOP_LYRIC_ENABLED]
            ?: prefs[booleanPreferencesKey("is_desktop_enable")]
            ?: false
    }
    val lockScreenLyricEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.LOCK_SCREEN_LYRIC_ENABLED] ?: false
    }
    val carBluetoothLyricEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.CAR_LYRIC_ENABLED] ?: false
    }
    val plazaLayoutMode: Flow<String> = context.dataStore.data.map {
        it[PreferencesKeys.PLAZA_LAYOUT_MODE] ?: PlazaLayoutModeDefault
    }

    // Token operations
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACCESS_TOKEN] = tokenCipher.encrypt(accessToken)
            if (refreshToken.isNullOrBlank()) {
                prefs.remove(PreferencesKeys.REFRESH_TOKEN)
            } else {
                prefs[PreferencesKeys.REFRESH_TOKEN] = tokenCipher.encrypt(refreshToken)
            }
        }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit {
            it[PreferencesKeys.ACCESS_TOKEN] = tokenCipher.encrypt(token)
        }
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

    suspend fun updateNotificationSettings(settings: NotificationPreferenceSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NOTIFY_COMMENT] = settings.commentEnabled
            prefs[PreferencesKeys.NOTIFY_LIKE] = settings.likeEnabled
            prefs[PreferencesKeys.NOTIFY_FOLLOW] = settings.followEnabled
            prefs[PreferencesKeys.NOTIFY_MENTION] = settings.mentionEnabled
            prefs[PreferencesKeys.NOTIFY_MESSAGE] = settings.messageEnabled
            prefs[PreferencesKeys.NOTIFY_FRIEND_REQUEST] = settings.friendRequestEnabled
            prefs[PreferencesKeys.NOTIFY_SYSTEM] = settings.systemEnabled
            prefs[PreferencesKeys.NOTIFY_MUSIC] = settings.musicEnabled
        }
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

    suspend fun updateThemePresetId(themeId: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_PRESET_ID] = themeId
            prefs.remove(PreferencesKeys.DYNAMIC_COLOR)
        }
    }

    suspend fun updateCustomThemeSeed(seedArgb: Long) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_THEME_SEED] = seedArgb
            prefs[PreferencesKeys.THEME_PRESET_ID] = AppThemeIds.CUSTOM
            prefs.remove(PreferencesKeys.DYNAMIC_COLOR)
        }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateDesktopLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DESKTOP_LYRIC_ENABLED] = enabled
            prefs.remove(booleanPreferencesKey("is_desktop_enable"))
        }
    }

    suspend fun updateLockScreenLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.LOCK_SCREEN_LYRIC_ENABLED] = enabled }
    }

    suspend fun updateCarBluetoothLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CAR_LYRIC_ENABLED] = enabled }
    }

    suspend fun updatePlazaLayoutMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.PLAZA_LAYOUT_MODE] = mode }
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

    suspend fun getPlaybackPlaylistSourceJson(): String? {
        return context.dataStore.data.first()[PreferencesKeys.PLAYBACK_PLAYLIST_SOURCE]
    }

    suspend fun savePlaybackPlaylistSourceJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) {
                prefs.remove(PreferencesKeys.PLAYBACK_PLAYLIST_SOURCE)
            } else {
                prefs[PreferencesKeys.PLAYBACK_PLAYLIST_SOURCE] = json
            }
        }
    }

    suspend fun clearPlaybackSnapshot() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.PLAYBACK_SNAPSHOT)
            prefs.remove(PreferencesKeys.PLAYBACK_CURRENT_INDEX)
            prefs.remove(PreferencesKeys.PLAYBACK_POSITION_MS)
            prefs.remove(PreferencesKeys.PLAYBACK_PLAYING_KEY)
            prefs.remove(PreferencesKeys.PLAYBACK_PLAYLIST_SOURCE)
        }
    }

    /** 读取音流上次选择的「听歌模式」mood（null = 默认模式）。 */
    suspend fun getStreamMood(): String? =
        context.dataStore.data.first()[PreferencesKeys.STREAM_MOOD]?.takeIf { it.isNotBlank() }

    /** 记住音流「听歌模式」；传 null/空串表示选择默认模式。 */
    suspend fun saveStreamMood(mood: String?) {
        context.dataStore.edit { prefs ->
            if (mood.isNullOrBlank()) {
                prefs.remove(PreferencesKeys.STREAM_MOOD)
            } else {
                prefs[PreferencesKeys.STREAM_MOOD] = mood
            }
        }
    }

    /**
     * 音流会话快照 JSON：冷启动用「同 seed + 同 exclude」重拉复现上一批，
     * 配合 index/positionMs 从上次的地方继续，避免每次重开都从第一批第一首重播。
     */
    suspend fun getStreamSessionJson(): String? =
        context.dataStore.data.first()[PreferencesKeys.STREAM_SESSION]?.takeIf { it.isNotBlank() }

    suspend fun saveStreamSessionJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.STREAM_SESSION] = json
        }
    }

    suspend fun clearStreamSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.STREAM_SESSION)
        }
    }

    /** 仅清除旧版队列 JSON blob，保留 index/position 等标量。 */
    suspend fun clearPlaybackSnapshotPlaylistJson() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.PLAYBACK_SNAPSHOT)
        }
    }

    /**
     * 聊天设置必须**按账号隔离**：key 里带上当前登录 user_id，
     * 否则换账号登录会继承上一个账号的免打扰/聊天背景（背景还可能指向别人目录下的文件）。
     *
     * 旧的 `mute_{peerId}` / `bg_{peerId}` key 自然失效（读回默认值），无需清理。
     */
    private fun ownerPrefix(prefs: Preferences): String =
        prefs[PreferencesKeys.USER_ID] ?: "0"

    private fun mutedKey(prefs: Preferences, peerUserId: Long) =
        booleanPreferencesKey("mute_${ownerPrefix(prefs)}_$peerUserId")

    private fun backgroundKey(prefs: Preferences, peerUserId: Long) =
        stringPreferencesKey("bg_${ownerPrefix(prefs)}_$peerUserId")

    // Chat settings - Mute
    suspend fun isConversationMuted(peerUserId: Long): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[mutedKey(prefs, peerUserId)] ?: false
    }

    suspend fun setConversationMuted(peerUserId: Long, muted: Boolean) {
        context.dataStore.edit { prefs ->
            val key = mutedKey(prefs, peerUserId)
            if (muted) {
                prefs[key] = true
            } else {
                prefs.remove(key)
            }
        }
    }

    fun observeConversationMuted(peerUserId: Long): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[mutedKey(prefs, peerUserId)] ?: false
        }

    // Chat settings - Background
    suspend fun getChatBackground(peerUserId: Long): String? {
        val prefs = context.dataStore.data.first()
        return prefs[backgroundKey(prefs, peerUserId)]
    }

    suspend fun setChatBackground(peerUserId: Long, path: String?) {
        context.dataStore.edit { prefs ->
            val key = backgroundKey(prefs, peerUserId)
            if (path != null) {
                prefs[key] = path
            } else {
                prefs.remove(key)
            }
        }
    }

    fun observeChatBackground(peerUserId: Long): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[backgroundKey(prefs, peerUserId)] }

    companion object {
        private const val RepeatModeDefault = "ALL"
        const val PlazaLayoutModeDefault = "staggered"
        const val DEFAULT_CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024
    }
}
