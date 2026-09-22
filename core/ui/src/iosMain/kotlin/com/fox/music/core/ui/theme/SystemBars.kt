package com.fox.music.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * iOS 没有 Android 那种系统栏着色 API：状态栏样式由 hosting controller 的
 * preferredStatusBarStyle / UIViewControllerBasedStatusBarAppearance 决定，
 * 且 SwiftUI 外壳已经接管。这里保持空实现即可。
 */
@Composable
actual fun ApplyFoxSystemBars(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
) = Unit
