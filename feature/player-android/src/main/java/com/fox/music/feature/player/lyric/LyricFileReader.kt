package com.fox.music.feature.player.lyric

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.fox.music.core.model.music.LyricTextNormalizer

internal sealed interface LyricFileRead {
    data class Ok(val name: String?, val text: String) : LyricFileRead
    data class Failed(val message: String) : LyricFileRead
}

internal fun readLyricFile(context: Context, uri: Uri): LyricFileRead {
    return try {
        val name = queryDisplayName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return LyricFileRead.Failed("读取歌词文件失败")
        if (bytes.isEmpty()) return LyricFileRead.Failed("歌词文件是空的")
        if (bytes.size > 512 * 1024) return LyricFileRead.Failed("歌词文件过大")
        val text = decodeLyricBytes(bytes)
        if (text.isBlank()) return LyricFileRead.Failed("歌词内容为空")
        if (LyricTextNormalizer.looksLikeBinary(text)) {
            return LyricFileRead.Failed("无法读取歌词文件，请选择 .lrc 或文本文件")
        }
        LyricFileRead.Ok(name, text)
    } catch (_: Exception) {
        LyricFileRead.Failed("读取歌词文件失败")
    }
}

private fun decodeLyricBytes(bytes: ByteArray): String {
    val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    if (!utf8.contains('\uFFFD')) return utf8
    return try {
        String(bytes, charset("GB18030")).removePrefix("\uFEFF")
    } catch (_: Exception) {
        utf8
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}
