package com.fox.music.iosshared

import com.fox.music.core.ui.theme.foxPageBackgroundArgb
import com.fox.music.core.ui.theme.foxTabGradientTopArgb

/**
 * iOS 宿主补色：与 [com.fox.music.core.ui.theme.FoxMusicTheme] 当前主题对齐。
 * 窗口/占位 Tab 用渐变顶色，避免状态栏一条、页面一块。
 *
 * 跟随 [FoxIosThemeState]（Swift AppearanceStore 推送）——
 * 设置里切换主题后窗口补色也跟着变，而不是永远停在默认「黑胶夜场」。
 */
object FoxIosTheme {

    /** 当前是否深色：显式覆盖优先，否则取 Swift 推送的系统深色状态。 */
    private val effectiveDark: Boolean
        get() = when (FoxIosThemeState.darkModeOverride) {
            0 -> false
            1 -> true
            else -> FoxIosThemeState.systemDark
        }

    /** 页面底色 ARGB —— colorScheme.background。 */
    fun pageBackgroundArgb(): Long = foxPageBackgroundArgb(
        darkTheme = effectiveDark,
        appThemeId = FoxIosThemeState.appThemeId,
    )

    /** Tab 渐变最顶端 ARGB —— primary 14% 叠在 background 上。 */
    fun tabGradientTopArgb(): Long = foxTabGradientTopArgb(
        darkTheme = effectiveDark,
        appThemeId = FoxIosThemeState.appThemeId,
    )
}
