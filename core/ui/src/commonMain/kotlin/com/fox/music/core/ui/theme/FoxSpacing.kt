package com.fox.music.core.ui.theme

import androidx.compose.ui.unit.dp

object FoxSpacing {
    val pagePadding = 20.dp
    val sectionSpacing = 28.dp
    val itemSpacing = 12.dp
    val cardPadding = 16.dp
    val heroTop = 12.dp
    val navInset = 12.dp
    /**
     * Tab 底栏 + MiniPlayer 叠层预留高度。
     * MiniPlayer(~76) + 间距 + 浮岛底栏(64+16) + 手势条 ≈ 200+，再留一点呼吸空间。
     */
    val tabChromeBottom = 220.dp
}
