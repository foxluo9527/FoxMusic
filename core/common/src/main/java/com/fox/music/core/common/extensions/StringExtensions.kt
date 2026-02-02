package com.fox.music.core.common.extensions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun String?.orEmpty(): String = this ?: ""

fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()

fun String.toDate(pattern: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"): Date? = try {
    SimpleDateFormat(pattern, Locale.getDefault()).parse(this)
} catch (e: Exception) {
    null
}

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun Long.formatDurationLong(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun Int.formatCount(): String = when {
    this >= 100_000_000 -> String.format(Locale.getDefault(), "%.1f亿", this / 100_000_000.0)
    this >= 10_000 -> String.format(Locale.getDefault(), "%.1f万", this / 10_000.0)
    else -> this.toString()
}
