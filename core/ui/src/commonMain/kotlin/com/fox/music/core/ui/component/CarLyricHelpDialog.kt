package com.fox.music.core.ui.component

import androidx.compose.runtime.Composable

/** 车载模式帮助 —— 长文告知，走 FoxInfoDialog。 */
@Composable
fun CarLyricHelpDialog(
    onDismiss: () -> Unit,
) {
    FoxInfoDialog(
        title = "车载模式帮助",
        text = listOf(
            "1. 在 Android Auto 中选择 FoxMusic 作为媒体源",
            "2. 车机端可浏览推荐歌单、搜索并播放音乐",
            "3. 手机端可通过「车载模式」入口横屏预览相同功能",
            "4. 开启「车载蓝牙歌词」后，蓝牙设备会显示动态歌词",
            "5. 需在 Android Auto 开发者选项中启用「未知来源」以调试",
            "6. 驾驶时请勿长时间注视屏幕，注意安全",
        ).joinToString("\n"),
        confirmText = "知道了",
        onDismiss = onDismiss,
    )
}
