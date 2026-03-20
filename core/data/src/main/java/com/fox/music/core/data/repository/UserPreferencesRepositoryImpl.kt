package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.PlayQuality
import com.fox.music.core.model.user.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: FoxPreferencesDataStore
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> =
        dataStore.accessToken.map { !it.isNullOrBlank() }
            .combine(dataStore.accessToken) { a, b -> Pair(a, b) }
            .combine(dataStore.userId) { t, id -> listOf(t.first, t.second, id) }
            .combine(dataStore.username) { list, name -> list + name }
            .combine(dataStore.darkMode) { list, v -> list + v }
            .combine(dataStore.autoPlay) { list, v -> list + v }
            .combine(dataStore.playQuality) { list, v -> list + v }
            .combine(dataStore.downloadQuality) { list, v -> list + v }
            .combine(dataStore.downloadOnWifiOnly) { list, v -> list + v }
            .combine(dataStore.showLyrics) { list, v -> list + v }
            .combine(dataStore.language) { list, v -> list + v }
            .map { list ->
                @Suppress("UNCHECKED_CAST")
                val isLoggedIn = list[0] as Boolean
                val token = list[1] as? String
                val userId = list[2] as? String
                val username = list[3] as? String
                val darkMode = list[4] as Boolean
                val autoPlay = list[5] as Boolean
                val playQuality = list[6] as String
                val downloadQuality = list[7] as String
                val downloadOnWifiOnly = list[8] as Boolean
                val showLyrics = list[9] as Boolean
                val language = list[10] as String
                UserPreferences(
                    isLoggedIn = isLoggedIn,
                    token = token,
                    userId = userId?.toLongOrNull(),
                    username = username,
                    darkMode = if (darkMode) DarkMode.DARK else DarkMode.LIGHT,
                    autoPlay = autoPlay,
                    playQuality = when (playQuality) {
                        "standard" -> PlayQuality.STANDARD
                        "lossless" -> PlayQuality.LOSSLESS
                        else -> PlayQuality.HIGH
                    },
                    downloadQuality = when (downloadQuality) {
                        "standard" -> PlayQuality.STANDARD
                        "lossless" -> PlayQuality.LOSSLESS
                        else -> PlayQuality.HIGH
                    },
                    downloadOnWifiOnly = downloadOnWifiOnly,
                    showLyrics = showLyrics,
                    language = language
                )
            }

    override suspend fun updateDarkMode(darkMode: DarkMode): Result<Unit> = suspendRunCatching {
        dataStore.updateDarkMode(darkMode == DarkMode.DARK)
        Unit
    }

    override suspend fun updateAutoPlay(enabled: Boolean): Result<Unit> = suspendRunCatching {
        dataStore.updateAutoPlay(enabled)
        Unit
    }

    override suspend fun updatePlayQuality(quality: PlayQuality): Result<Unit> =
        suspendRunCatching {
            dataStore.updatePlayQuality(
                when (quality) {
                    PlayQuality.STANDARD -> "standard"
                    PlayQuality.LOSSLESS -> "lossless"
                    PlayQuality.HIGH -> "high"
                }
            )
            Unit
        }

    override suspend fun updateDownloadQuality(quality: PlayQuality): Result<Unit> =
        suspendRunCatching {
            dataStore.updateDownloadQuality(
                when (quality) {
                    PlayQuality.STANDARD -> "standard"
                    PlayQuality.LOSSLESS -> "lossless"
                    PlayQuality.HIGH -> "high"
                }
            )
            Unit
        }

    override suspend fun updateShowLyrics(enabled: Boolean): Result<Unit> = suspendRunCatching {
        dataStore.updateShowLyrics(enabled)
        Unit
    }

    override suspend fun updateLanguage(language: String): Result<Unit> = suspendRunCatching {
        dataStore.updateLanguage(language)
        Unit
    }

    override suspend fun updateDownloadOnWifiOnly(enabled: Boolean): Result<Unit> =
        suspendRunCatching {
            dataStore.updateDownloadOnWifiOnly(enabled)
            Unit
        }
}
