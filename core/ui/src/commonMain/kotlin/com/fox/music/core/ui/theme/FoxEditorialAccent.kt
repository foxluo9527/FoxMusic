package com.fox.music.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * v10 编辑排版体系的唯一强调色 —— **跟随用户在「主题设置」里选的配色**。
 *
 * 设计决策（2026-10-25 定）：设计稿里的陶土 #B8513A 是「默认色板」下的强调色，
 * 不是硬编码常量。项目有 12 作家色板 + 自定义取色的主题系统（用户明确要求
 * 「让用户选择主题色」），整套系统的意义就在于强调色可切换。
 *
 * 因此这里取 [MaterialTheme.colorScheme].primary：
 * - 默认陶土色板下它**就是** #B8513A，与设计稿完全一致；
 * - 用户切到「黛蓝 / 苔绿 …」时 CTA、选中态、分区编号会整体跟随，主题系统才成立；
 * - 深色模式自动继承 [AppThemePresets] 里 colorSchemeFromSeed 的 boostForDark
 *   （HSV 保色相提亮、彩度兜底），不会出现「深色下被洗成灰白」的老问题。
 *
 * 用法：只出现在 CTA / 选中态 / 正在播放三处，不要滥用。
 */
val foxEditorialAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
