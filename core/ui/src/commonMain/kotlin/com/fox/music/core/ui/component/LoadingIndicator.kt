package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 加载指示器（跨端契约）。
 *
 * Android actual 保留了 Lottie 动画分支与骨架屏默认行为（useSkeleton = true），
 * iOS actual 只有骨架屏 / 转圈两种纯 Compose 实现——Lottie 是 Android 专属依赖。
 */
@Composable
expect fun LoadingIndicator(
    modifier: Modifier = Modifier.fillMaxSize(),
    useLottie: Boolean = false,
    useSkeleton: Boolean = true,
)
