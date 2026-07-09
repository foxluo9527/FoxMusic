package com.fox.music.widget

import android.content.Context
import android.content.res.Configuration
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.UserPreferences
import com.fox.music.core.ui.theme.AppThemeColorResolver
import com.fox.music.core.ui.theme.FoxWidgetColors
import com.fox.music.core.ui.theme.resolveDarkTheme
import androidx.core.content.edit

object WidgetThemeStore {
    fun sync(context: Context, preferences: UserPreferences, isSystemDark: Boolean) {
        context.getSharedPreferences(WidgetStateKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(WidgetStateKeys.APP_THEME_ID, preferences.appThemeId)
                    .putLong(WidgetStateKeys.CUSTOM_THEME_SEED, preferences.customThemeSeedArgb)
                    .putString(WidgetStateKeys.DARK_MODE, preferences.darkMode.name)
                    .putBoolean(
                        WidgetStateKeys.DARK_THEME,
                        preferences.resolveDarkTheme(isSystemDark),
                    )
            }
    }

    fun read(context: Context): FoxWidgetColors {
        val prefs = context.getSharedPreferences(WidgetStateKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(WidgetStateKeys.APP_THEME_ID, AppThemeIds.DEFAULT)
            ?: AppThemeIds.DEFAULT
        val seed = prefs.getLong(WidgetStateKeys.CUSTOM_THEME_SEED, 0xFF6C63FF)
        val darkTheme = resolveDarkTheme(
            darkModeName = prefs.getString(WidgetStateKeys.DARK_MODE, DarkMode.FOLLOW_SYSTEM.name),
            isSystemDark = isSystemDark(context),
            cachedDarkTheme = prefs.getBoolean(WidgetStateKeys.DARK_THEME, false),
        )
        return AppThemeColorResolver.widgetColors(themeId, seed, darkTheme)
    }

    private fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun resolveDarkTheme(
        darkModeName: String?,
        isSystemDark: Boolean,
        cachedDarkTheme: Boolean,
    ): Boolean {
        val darkMode = runCatching {
            DarkMode.valueOf(darkModeName ?: DarkMode.FOLLOW_SYSTEM.name)
        }.getOrDefault(DarkMode.FOLLOW_SYSTEM)
        return when (darkMode) {
            DarkMode.DARK -> true
            DarkMode.LIGHT -> false
            DarkMode.FOLLOW_SYSTEM -> isSystemDark
            else -> cachedDarkTheme
        }
    }
}
