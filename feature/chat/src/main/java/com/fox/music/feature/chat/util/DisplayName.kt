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

fun formatMessageDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val millis = parseMessageTimestampMillis(dateStr) ?: return dateStr
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val date = zoned.toLocalDate()
    val today = LocalDate.now(ZoneId.systemDefault())
    val timeText = zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    return when {
        date == today -> timeText
        date.year == today.year -> zoned.format(
            DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.getDefault()),
        )
        else -> zoned.format(
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.getDefault()),
        )
    }
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
