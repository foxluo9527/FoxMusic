package com.fox.music.iosshared

import com.fox.music.core.ui.theme.foxPageBackgroundArgb
import com.fox.music.core.ui.theme.foxTabGradientTopArgb

/**
 * iOS 宿主补色：与 [com.fox.music.core.ui.theme.FoxMusicTheme] 默认预设（黑胶夜场）对齐。
 * 窗口/占位 Tab 用渐变顶色，避免状态栏一条、页面一块。
 */
object FoxIosTheme {

    /** 页面底色 ARGB —— colorScheme.background。 */
    fun pageBackgroundArgb(): Long = foxPageBackgroundArgb()

    /** Tab 渐变最顶端 ARGB —— primary 14% 叠在 background 上。 */
    fun tabGradientTopArgb(): Long = foxTabGradientTopArgb()
}
