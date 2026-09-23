package com.fox.music.feature.chat.util

import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.core.model.chat.SearchedUser
import com.fox.music.core.model.user.User
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

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

// java.time 在 K/N 不可用（批次 20 踩坑），统一改用 kotlinx.datetime。
private fun localTimeZone(): TimeZone = TimeZone.currentSystemDefault()

private fun formatHourMinute(instant: Instant): String {
    val dt = instant.toLocalDateTime(localTimeZone())
    return "${dt.hour.pad2()}:${dt.minute.pad2()}"
}

private fun Int.pad2(): String = toString().padStart(2, '0')

fun formatMessageDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val millis = parseMessageTimestampMillis(dateStr) ?: run {
        // 最终兜底：尝试直接将字符串作为 epoch millis 解析
        dateStr.toLongOrNull()?.let { raw ->
            val epochMs = if (dateStr.length <= 10) raw * 1000L else raw
            return formatHourMinute(Instant.fromEpochMilliseconds(epochMs))
        }
        return dateStr
    }
    val instant = Instant.fromEpochMilliseconds(millis)
    val date = instant.toLocalDateTime(localTimeZone()).date
    val today = Clock.System.now().toLocalDateTime(localTimeZone()).date
    val daysDiff = date.daysUntil(today)
    val timeText = formatHourMinute(instant)
    return when (daysDiff) {
        0 -> timeText
        1 -> "昨天 $timeText"
        in 2..6 -> "${CHINESE_WEEKDAYS[date.dayOfWeek.isoDayNumber]} $timeText"
        else -> "${date.year}-${date.monthNumber.pad2()}-${date.dayOfMonth.pad2()} $timeText"
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
    val currentDate = Instant.fromEpochMilliseconds(currentMillis)
        .toLocalDateTime(localTimeZone()).date
    val previousDate = Instant.fromEpochMilliseconds(previousMillis)
        .toLocalDateTime(localTimeZone()).date
    return currentDate != previousDate
}

private fun parseMessageTimestampMillis(dateStr: String): Long? {
    dateStr.toLongOrNull()?.let { raw ->
        return if (dateStr.length <= 10) raw * 1_000L else raw
    }
    return try {
        Instant.parse(dateStr).toEpochMilliseconds()
    } catch (_: Exception) {
        try {
            LocalDate.parse(dateStr.take(10))
                .atStartOfDayIn(localTimeZone())
                .toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
    }
}
