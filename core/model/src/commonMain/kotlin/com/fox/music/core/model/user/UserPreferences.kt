package com.fox.music.core.model.user

data class UserPreferences(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val autoPlay: Boolean = true,
    val playQuality: PlayQuality = PlayQuality.HIGH,
    val downloadQuality: PlayQuality = PlayQuality.LOSSLESS,
    val downloadOnWifiOnly: Boolean = true,
    val showLyrics: Boolean = true,
    val appThemeId: String = AppThemeIds.DEFAULT,
    val customThemeSeedArgb: Long = 0xFF6C63FF,
    /** 主题「氛围」强度：柔和 0.08 / 均衡 0.12 / 浓郁 0.18（与 themeId 同处存储） */
    val themeIntensity: Float = AppThemeIntensity.DEFAULT,
    val desktopLyricEnabled: Boolean = false,
    val lockScreenLyricEnabled: Boolean = false,
    val carBluetoothLyricEnabled: Boolean = false,
    val notificationSettings: NotificationPreferenceSettings = NotificationPreferenceSettings(),
    val language: String = "zh-CN",
    val cacheMaxBytes: Long = 2L * 1024 * 1024 * 1024,
)

enum class DarkMode {
    LIGHT,
    DARK,
    FOLLOW_SYSTEM
}

enum class PlayQuality {
    STANDARD,
    HIGH,
    LOSSLESS
}
