package com.fox.music.core.model

data class UserPreferences(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val refreshToken: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val autoPlay: Boolean = true,
    val playQuality: PlayQuality = PlayQuality.HIGH,
    val downloadQuality: PlayQuality = PlayQuality.LOSSLESS,
    val downloadOnWifiOnly: Boolean = true,
    val showLyrics: Boolean = true,
    val language: String = "zh-CN"
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
