package com.fox.music.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.fox.music.core.model.user.AppThemeIds

@Composable
fun FoxMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appThemeId: String = AppThemeIds.DEFAULT,
    customThemeSeedArgb: Long = 0xFFE8A838,
    content: @Composable () -> Unit,
) {
    val colorScheme = resolveAppColorScheme(
        darkTheme = darkTheme,
        appThemeId = appThemeId,
        customThemeSeedArgb = customThemeSeedArgb,
    )

    ApplyFoxSystemBars(colorScheme = colorScheme, darkTheme = darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FoxMusicTypography,
        shapes = FoxShapes,
        content = content,
    )
}

@Composable
fun resolveColorScheme(
    darkTheme: Boolean,
    appThemeId: String,
    customThemeSeedArgb: Long,
): ColorScheme = resolveAppColorScheme(darkTheme, appThemeId, customThemeSeedArgb)

/**
 * 把系统栏（状态栏/导航栏）与主题对齐。
 *
 * 这是平台能力：Android 需要操作 Activity 的 Window，iOS 没有对应概念，
 * 所以走 expect/actual —— iOS 侧为空实现。
 */
@Composable
internal expect fun ApplyFoxSystemBars(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
)

@Composable
fun previewColorScheme(
    appThemeId: String,
    customThemeSeedArgb: Long,
    darkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme = resolveColorScheme(darkTheme, appThemeId, customThemeSeedArgb)
