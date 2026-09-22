package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.AppThemePreset
import com.fox.music.core.ui.theme.AppThemePresets
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ThemeSettingsState(
    val appThemeId: String = AppThemeIds.DEFAULT,
    val customThemeSeedArgb: Long = 0xFF6C63FF,
    val presets: List<AppThemePreset> = AppThemePresets.catalog,
    val customHueOptions: List<Long> = AppThemePresets.customHueOptions,
) : UiState

sealed interface ThemeSettingsIntent : UiIntent {
    data class SelectPreset(val themeId: String) : ThemeSettingsIntent
    data class SelectCustomHue(val seedArgb: Long) : ThemeSettingsIntent
}

sealed interface ThemeSettingsEffect : UiEffect {
    data object RecreateActivity : ThemeSettingsEffect
}

@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<ThemeSettingsState, ThemeSettingsIntent, ThemeSettingsEffect>(
    initialState = ThemeSettingsState(),
) {

    init {
        userPreferencesRepository.userPreferences
            .onEach { prefs ->
                updateState {
                    copy(
                        appThemeId = prefs.appThemeId,
                        customThemeSeedArgb = prefs.customThemeSeedArgb,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: ThemeSettingsIntent) {
        when (intent) {
            is ThemeSettingsIntent.SelectPreset -> selectPreset(intent.themeId)
            is ThemeSettingsIntent.SelectCustomHue -> selectCustomHue(intent.seedArgb)
        }
    }

    fun themeLabel(themeId: String): String =
        if (themeId == AppThemeIds.CUSTOM) "个性自定义" else AppThemePresets.presetName(themeId)

    private fun selectPreset(themeId: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateAppTheme(themeId)
                .onSuccess { sendEffect(ThemeSettingsEffect.RecreateActivity) }
        }
    }

    private fun selectCustomHue(seedArgb: Long) {
        viewModelScope.launch {
            userPreferencesRepository.updateAppTheme(AppThemeIds.CUSTOM, seedArgb)
                .onSuccess { sendEffect(ThemeSettingsEffect.RecreateActivity) }
        }
    }
}
