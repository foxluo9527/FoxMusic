package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.PlayQuality
import com.fox.music.core.model.user.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {

    val userPreferences: Flow<UserPreferences>

    suspend fun updateDarkMode(darkMode: DarkMode): Result<Unit>

    suspend fun updateAutoPlay(enabled: Boolean): Result<Unit>

    suspend fun updatePlayQuality(quality: PlayQuality): Result<Unit>

    suspend fun updateDownloadQuality(quality: PlayQuality): Result<Unit>

    suspend fun updateShowLyrics(enabled: Boolean): Result<Unit>

    suspend fun updateLanguage(language: String): Result<Unit>

    suspend fun updateDownloadOnWifiOnly(enabled: Boolean): Result<Unit>
}
