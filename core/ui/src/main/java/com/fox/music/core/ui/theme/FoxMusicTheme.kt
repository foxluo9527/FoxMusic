package com.fox.music.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Purple500,
    onPrimary = Color.White,
    primaryContainer = Purple200,
    onPrimaryContainer = Purple700,
    secondary = Pink500,
    onSecondary = Color.White,
    secondaryContainer = Pink200,
    onSecondaryContainer = Pink700,
    tertiary = Teal500,
    onTertiary = Color.White,
    tertiaryContainer = Teal200,
    onTertiaryContainer = Teal700,
    background = Gray50,
    onBackground = Gray900,
    surface = Color.White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    outline = Gray400,
    error = ErrorRedLight,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple200,
    onPrimary = Purple700,
    primaryContainer = Purple700,
    onPrimaryContainer = Purple200,
    secondary = Pink200,
    onSecondary = Pink700,
    secondaryContainer = Pink700,
    onSecondaryContainer = Pink200,
    tertiary = Teal200,
    onTertiary = Teal700,
    tertiaryContainer = Teal700,
    onTertiaryContainer = Teal200,
    background = DarkBackground,
    onBackground = Gray200,
    surface = DarkSurface,
    onSurface = Gray200,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Gray400,
    outline = Gray500,
    error = ErrorRed,
    onError = Gray900,
)

@Composable
fun FoxMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FoxMusicTypography,
        content = content,
    )
}
