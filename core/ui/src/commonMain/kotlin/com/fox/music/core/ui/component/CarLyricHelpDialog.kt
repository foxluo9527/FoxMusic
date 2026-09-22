package com.fox.music.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun CarLyricHelpDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("车载模式帮助") },
        text = {
            Text(
                "1. 在 Android Auto 中选择 FoxMusic 作为媒体源\n" +
                    "2. 车机端可浏览推荐歌单、搜索并播放音乐\n" +
                    "3. 手机端可通过「车载模式」入口横屏预览相同功能\n" +
                    "4. 开启「车载蓝牙歌词」后，蓝牙设备会显示动态歌词\n" +
                    "5. 需在 Android Auto 开发者选项中启用「未知来源」以调试\n" +
                    "6. 驾驶时请勿长时间注视屏幕，注意安全",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
    )
}
