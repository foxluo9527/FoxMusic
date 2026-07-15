package com.fox.music.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

@Composable
private fun ApplyFoxSystemBars(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val controller = WindowCompat.getInsetsController(window, view)
        val useLightIcons = colorScheme.surface.luminance() < 0.5f
        controller.isAppearanceLightStatusBars = !useLightIcons
        controller.isAppearanceLightNavigationBars = !darkTheme && colorScheme.surface.luminance() > 0.5f
    }
}

@Composable
fun previewColorScheme(
    appThemeId: String,
    customThemeSeedArgb: Long,
    darkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme = resolveColorScheme(darkTheme, appThemeId, customThemeSeedArgb)
