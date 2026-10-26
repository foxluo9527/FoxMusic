package com.fox.music.core.ui.util

/**
 * 曲目时长文案：毫秒 → `m:ss`（超过 1 小时才出 `h:mm:ss`）。
 *
 * 服务端 `music.duration` 单位是**毫秒**，`0` 表示未知，此时返回空串让 UI 直接不占位。
 * 数字统一按等宽（tabular）排版，见 [com.fox.music.core.ui.component.MusicListItem]。
 */
fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val ss = if (seconds < 10) "0$seconds" else "$seconds"
    return if (hours > 0L) {
        val mm = if (minutes < 10) "0$minutes" else "$minutes"
        "$hours:$mm:$ss"
    } else {
        "$minutes:$ss"
    }
}
