package com.fox.music.feature.chat.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

fun formatMediaSize(context: Context, uri: Uri): String? {
    val bytes = resolveContentLength(context, uri) ?: return null
    if (bytes <= 0L) return null
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val size = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format("大小：%.1f %s", size, units[digitGroups])
}

private fun resolveContentLength(context: Context, uri: Uri): Long? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getLong(index).takeIf { it > 0L }
        }
    }
    if (uri.scheme == "file") {
        uri.path?.let { File(it).length().takeIf { size -> size > 0L } }?.let { return it }
    }
    return null
}
