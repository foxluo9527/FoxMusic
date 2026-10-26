package com.fox.music.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 4pt 基线间距。
 *
 * 2026-10 改版把"竖向节奏"固定成三段韵律（原型 01 首页）：
 * 板块之间 [sectionGap] / 标题到内容 [headerGap] / 列表行高 [rowHeight]。
 * 之前各间距都是 8/12 的均质堆叠，扫下去没有落点，是"AI 味"的来源之一。
 */
object FoxSpacing {
    /** 页面左右安全边距（原型 24） */
    val pagePadding = 24.dp

    /** 板块之间 */
    val sectionGap = 20.dp

    /** 板块标题 → 内容 */
    val headerGap = 11.dp

    /** 列表行高（含上下留白） */
    val rowHeight = 56.dp

    /** 横向轨道小卡（最近在听 74dp） */
    val railItemSmall = 74.dp

    /** 横向轨道大卡（精选歌单 108dp） */
    val railItemLarge = 108.dp

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
