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
    /** 氛围三档：柔和 0.06 / 均衡 0.22 / 浓郁 0.48（v12 拉开的真实色差） */
    val themeIntensity: Float = AppThemeIntensity.DEFAULT,
    /** 深色模式档位（设计稿①03）；FOLLOW_SYSTEM 时由 UI 层按系统解析成开关状态。 */
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
) : UiState

/**
 * 落盘意图。
 *
 * v12 验收返工：主题页改成「草稿 → 应用」两段式，这些 intent 只有点「应用」时才会被
 * dispatch。页面内的取色 / 浓度 / 深色开关一律只改本地草稿，**不经过这里**，因此也
 * 不会在拖动取色盘时把 DataStore 写穿。
 */
sealed interface ThemeSettingsIntent : UiIntent {
    data class SelectPreset(val themeId: String) : ThemeSettingsIntent
    data class SelectCustomHue(val seedArgb: Long) : ThemeSettingsIntent
    data class SelectIntensity(val intensity: Float) : ThemeSettingsIntent
    /** [enabled] = true 写 DARK，false 写 LIGHT（开关语义，不再保留"跟随系统"）。 */
    data class SetDarkMode(val enabled: Boolean) : ThemeSettingsIntent
}

/**
 * 本页**没有**一次性副作用。
 *
 * 旧实现里每次落盘都会 `sendEffect(RecreateActivity)`，让 Activity 重建来重建整棵
 * ColorScheme 树 —— 这正是「点预设色直接崩溃」的根因之一：recreate 期间导航图被拆，
 * 残留的 navigate 调用抛 `Navigation graph has not been set for NavController`。
 * 现在主题由应用层的 userPreferences flow 自然驱动重绘，这里保留空契约只为满足
 * MviViewModel 的类型参数。
 */
sealed interface ThemeSettingsEffect : UiEffect

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
        }
    }

    private fun selectCustomHue(seedArgb: Long) {
        viewModelScope.launch {
            userPreferencesRepository.updateAppTheme(AppThemeIds.CUSTOM, seedArgb)
        }
    }

    /** 氛围只改派生强度的系数，不换主色。 */
    private fun selectIntensity(intensity: Float) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeIntensity(intensity)
        }
    }

    /**
     * 深色模式开关（设计稿①03）。
     *
     * 只写 DARK / LIGHT 两档：开关是二态控件，写回"跟随系统"会让下一次点击无反馈。
     * 主 Activity 的 darkTheme 直接来自 preferences 流，偏好一落盘就重组，无需 recreate。
     */
    private fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDarkMode(
                if (enabled) DarkMode.DARK else DarkMode.LIGHT,
            )
        }
    }
}
