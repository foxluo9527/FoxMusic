package com.fox.music.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LyricHelpDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("锁屏歌词帮助") },
        text = {
            Text(
                "1. 确保已开启「锁屏歌词」开关\n" +
                    "2. 解锁手机后将自动弹出歌词页（需正在播放音乐）\n" +
                    "3. 关闭省电模式或将 FoxMusic 加入后台白名单\n" +
                    "4. 部分厂商系统可能限制解锁后自动弹出页面，可尝试关闭电池优化\n" +
                    "5. 点击歌词页空白处或返回键可关闭",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
    )
}
