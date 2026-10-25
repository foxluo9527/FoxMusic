package com.fox.music.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * v10 编辑排版体系的唯一强调色：浅色陶土 #B8513A / 深色 #E2563C。
 *
 * 与「主题设置」里用户可选的配色解耦：设计稿把这处强调色写成固定令牌
 * （只出现在 CTA / 选中态 / 正在播放三处），所以不取 `colorScheme.primary` ——
 * 否则用户把主题切成「黛蓝」时，v10 页面的 CTA 会跟着变蓝，与稿面陶土不符。
 *
 * 深浅判定读当前 background 的亮度，跟随 App 的深色模式开关（而不是系统设置）。
 */
val foxEditorialAccent: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        AccentTerracottaBright
    } else {
        AccentTerracotta
    }
