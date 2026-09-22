package com.fox.music.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.UserPreferences

data class FoxWidgetColors(
    val panelBackground: Int,
    val titleText: Int,
    val bodyText: Int,
    val highlightText: Int,
    val lyricMuted: Int,
    val lyricActive: Int,
    val controlTint: Int,
    val favoriteTint: Int,
    val placeholderBackground: Int,
    val placeholderText: Int,
)

fun resolveAppColorScheme(
    darkTheme: Boolean,
    appThemeId: String,
    customThemeSeedArgb: Long,
): ColorScheme {
    if (appThemeId == AppThemeIds.CUSTOM) {
        return colorSchemeFromSeed(customThemeSeedArgb, darkTheme)
    }
    return AppThemePresets.colorScheme(appThemeId, darkTheme)
}

fun UserPreferences.resolveDarkTheme(isSystemDark: Boolean): Boolean = when (darkMode) {
    DarkMode.DARK -> true
    DarkMode.LIGHT -> false
    DarkMode.FOLLOW_SYSTEM -> isSystemDark
}

object AppThemeColorResolver {
    fun widgetColors(
        appThemeId: String,
        customThemeSeedArgb: Long,
        darkTheme: Boolean,
    ): FoxWidgetColors {
        val scheme = resolveAppColorScheme(darkTheme, appThemeId, customThemeSeedArgb)
        return FoxWidgetColors(
            panelBackground = scheme.surface.toArgb(),
            titleText = scheme.onSurface.toArgb(),
            bodyText = scheme.onSurfaceVariant.toArgb(),
            highlightText = scheme.primary.toArgb(),
            lyricMuted = scheme.onSurfaceVariant.toArgb(),
            lyricActive = scheme.onSurface.toArgb(),
            controlTint = scheme.onSurface.toArgb(),
            favoriteTint = scheme.primary.toArgb(),
            placeholderBackground = scheme.primaryContainer.toArgb(),
            placeholderText = scheme.primary.toArgb(),
        )
    }
}
