package com.fox.music.core.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun ApplyFoxSystemBars(
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
