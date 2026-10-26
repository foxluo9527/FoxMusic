package com.fox.music.core.ui.component

import androidx.compose.runtime.Composable

/** 锁屏歌词帮助 —— 长文告知，走 FoxInfoDialog（正文可滚动，不再被 AlertDialog 裁掉）。 */
@Composable
fun LyricHelpDialog(
    onDismiss: () -> Unit,
) {
    FoxInfoDialog(
        title = "锁屏歌词帮助",
        text = listOf(
            "1. 确保已开启「锁屏歌词」开关",
            "2. 解锁手机后将自动弹出歌词页（需正在播放音乐）",
            "3. 关闭省电模式或将 FoxMusic 加入后台白名单",
            "4. 部分厂商系统可能限制解锁后自动弹出页面，可尝试关闭电池优化",
            "5. 点击歌词页空白处或返回键可关闭",
        ).joinToString("\n"),
        confirmText = "知道了",
        onDismiss = onDismiss,
    )
}
