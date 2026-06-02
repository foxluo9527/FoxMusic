package com.fox.music.feature.chat.util

import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.core.model.chat.SearchedUser
import com.fox.music.core.model.user.User
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun SearchedUser.displayName(): String =
    mark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: "用户"

fun Friend.displayName(): String =
    mark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }
        ?: "用户"

fun FriendRequest.displayName(): String =
    nickname?.takeIf { it.isNotBlank() } ?: "用户"

fun User.displayName(): String =
    nickname?.takeIf { it.isNotBlank() }
        ?: username.takeIf { it.isNotBlank() }
        ?: "用户"

private val CHINESE_WEEKDAYS = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun formatMessageDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val millis = parseMessageTimestampMillis(dateStr) ?: run {
        // 最终兜底：尝试直接将字符串作为 epoch millis 解析
        dateStr.toLongOrNull()?.let { raw ->
            val epochMs = if (dateStr.length <= 10) raw * 1000L else raw
            val zoned = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            return zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        }
        return dateStr
    }
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val date = zoned.toLocalDate()
    val today = LocalDate.now(ZoneId.systemDefault())
    val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()
    val timeText = zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    return when (daysDiff) {
        0 -> timeText
        1 -> "昨天 $timeText"
        in 2..6 -> "${CHINESE_WEEKDAYS[date.dayOfWeek.value]} $timeText"
        else -> zoned.format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()),
        )
    }
}

fun parseTimestampMillis(dateStr: String?): Long? = dateStr?.let { parseMessageTimestampMillis(it) }

/**
 * Determine whether a message should display its timestamp.
 *
 * Rules:
 * - The very first message always shows time.
 * - If the current message's time differs from the previous message's time by ≥ 5 minutes, show time.
 * - If the two messages fall on different calendar days, always show time.
 */
fun shouldShowMessageTime(currentMillis: Long, previousMillis: Long?): Boolean {
    if (previousMillis == null) return true
    val fiveMinutesMs = 5 * 60 * 1_000L
    if (kotlin.math.abs(currentMillis - previousMillis) >= fiveMinutesMs) return true
    val currentDate = Instant.ofEpochMilli(currentMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val previousDate = Instant.ofEpochMilli(previousMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return currentDate != previousDate
}

private fun parseMessageTimestampMillis(dateStr: String): Long? {
    dateStr.toLongOrNull()?.let { raw ->
        return if (dateStr.length <= 10) raw * 1_000L else raw
    }
    return try {
        Instant.parse(dateStr).toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDate.parse(dateStr.take(10))
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
