package com.fox.music.iosshared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.fox.music.core.ui.theme.FoxMusicTheme
import com.fox.music.core.ui.theme.foxTabPageBackground
import platform.UIKit.UIViewController

/**
 * iOS Compose 宿主：套 [FoxMusicTheme] + Tab 渐变底，并把内容顶开状态栏。
 * Swift 侧应对 Representable `.ignoresSafeArea(edges: .top)`，让渐变画进状态栏后方。
 *
 * [applyTabGradient] 为 false 时只套主题（登录页自带氛围底）。
 *
 * [pageOwnsTopBar]：页面自带会消费状态栏 inset 的 M3 `TopAppBar`（搜索 / 歌手详情 /
 * 榜单详情等二级页）时传 true —— 宿主**不再** statusBarsPadding。
 * 否则会出现：状态栏后面是渐变底色、导航栏是 surface 色，两条色带之间一条缝，
 * 且返回行被整体压到状态栏下方（顶部凭空多一段距离）。
 * 交给 TopAppBar 自己处理 inset 后，它的 surface 会直接铺进状态栏区域，颜色连成一体。
 */
fun foxComposeViewController(
    applyTabGradient: Boolean = true,
    pageOwnsTopBar: Boolean = false,
    content: @Composable () -> Unit,
): UIViewController = ComposeUIViewController {
    FoxMusicTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (applyTabGradient) Modifier.foxTabPageBackground() else Modifier)
                .then(
                    if (applyTabGradient && !pageOwnsTopBar) Modifier.statusBarsPadding() else Modifier
                ),
        ) {
            content()
        }
    }
}
