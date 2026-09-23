package com.fox.music.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.fox.music.core.model.user.AppThemeIds

/** Tab 页顶光渐变：primary 罩层在 22% 处落到页面底色（与历史 MainScreenWithTabBar 一致）。 */
const val FoxTabGradientMidStop = 0.22f
const val FoxTabGradientPrimaryAlpha = 0.14f

fun foxTabPageGradient(scheme: ColorScheme): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to scheme.primary.copy(alpha = FoxTabGradientPrimaryAlpha),
        FoxTabGradientMidStop to scheme.background,
        1f to scheme.background,
    ),
)

/**
 * Tab 根页背景：先铺不透明 background，再叠半透明 primary 顶光。
 * feature Content 不要再刷实心 colorScheme.background，否则会盖掉这层渐变。
 */
fun Modifier.foxTabPageBackground(scheme: ColorScheme): Modifier =
    this
        .background(scheme.background)
        .background(foxTabPageGradient(scheme))

@Composable
fun Modifier.foxTabPageBackground(): Modifier =
    foxTabPageBackground(MaterialTheme.colorScheme)

/** 渐变最顶端像素（primary 14% src-over 在 background 上），给窗口/状态栏补色。 */
fun foxTabGradientTopColor(scheme: ColorScheme): Color =
    lerp(scheme.background, scheme.primary, FoxTabGradientPrimaryAlpha)

fun foxTabGradientTopArgb(
    darkTheme: Boolean = false,
    appThemeId: String = AppThemeIds.DEFAULT,
    customThemeSeedArgb: Long = 0xFFE8A838,
): Long {
    val scheme = resolveAppColorScheme(darkTheme, appThemeId, customThemeSeedArgb)
    return foxTabGradientTopColor(scheme).toArgb().toLong() and 0xFFFFFFFFL
}

fun foxPageBackgroundArgb(
    darkTheme: Boolean = false,
    appThemeId: String = AppThemeIds.DEFAULT,
    customThemeSeedArgb: Long = 0xFFE8A838,
): Long {
    val scheme = resolveAppColorScheme(darkTheme, appThemeId, customThemeSeedArgb)
    return scheme.background.toArgb().toLong() and 0xFFFFFFFFL
}
