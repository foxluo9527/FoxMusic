package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.AppThemeIntensity
import com.fox.music.core.model.user.AppThemePreset
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.ui.theme.AppThemePresets
import com.fox.music.core.ui.theme.SeedSwatch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ThemeSettingsState(
    val appThemeId: String = AppThemeIds.DEFAULT,
    val customThemeSeedArgb: Long = 0xFF6C63FF,
    val presets: List<AppThemePreset> = AppThemePresets.catalog,
    /** 12 支「作家色板」（带中文色名，设计稿①） */
    val seedSwatches: List<SeedSwatch> = AppThemePresets.customSeedSwatches,
    /** 氛围三档：柔和 0.08 / 均衡 0.12 / 浓郁 0.18 */
    val themeIntensity: Float = AppThemeIntensity.DEFAULT,
    /** 深色模式档位（设计稿①03）；FOLLOW_SYSTEM 时由 UI 层按系统解析成开关状态。 */
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
) : UiState

sealed interface ThemeSettingsIntent : UiIntent {
    data class SelectPreset(val themeId: String) : ThemeSettingsIntent
    data class SelectCustomHue(val seedArgb: Long) : ThemeSettingsIntent
    data class SelectIntensity(val intensity: Float) : ThemeSettingsIntent
    /** [enabled] = true 写 DARK，false 写 LIGHT（开关语义，不再保留"跟随系统"）。 */
    data class SetDarkMode(val enabled: Boolean) : ThemeSettingsIntent
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
                        themeIntensity = prefs.themeIntensity,
                        darkMode = prefs.darkMode,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: ThemeSettingsIntent) {
        when (intent) {
            is ThemeSettingsIntent.SelectPreset -> selectPreset(intent.themeId)
            is ThemeSettingsIntent.SelectCustomHue -> selectCustomHue(intent.seedArgb)
            is ThemeSettingsIntent.SelectIntensity -> selectIntensity(intent.intensity)
            is ThemeSettingsIntent.SetDarkMode -> setDarkMode(intent.enabled)
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

    /** 氛围只改派生强度，不换主色；同样要 recreate 让整棵树的 ColorScheme 重建。 */
    private fun selectIntensity(intensity: Float) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeIntensity(intensity)
                .onSuccess { sendEffect(ThemeSettingsEffect.RecreateActivity) }
        }
    }

    /**
     * 深色模式开关（设计稿①03）。
     *
     * 只写 DARK / LIGHT 两档：开关是二态控件，写回"跟随系统"会让下一次点击无反馈。
     * 不需要 recreate —— 主Activity 的 darkTheme 直接来自 preferences 流，偏好一落盘就重组。
     */
    private fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDarkMode(
                if (enabled) DarkMode.DARK else DarkMode.LIGHT,
            )
        }
    }
}
