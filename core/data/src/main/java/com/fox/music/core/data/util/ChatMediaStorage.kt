package com.fox.music.core.data.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMediaStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun persistUri(uri: Uri, extension: String = "bin"): Uri {
        if (uri.scheme == "file") return uri
        val dir = File(context.filesDir, "chat_media").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取媒体文件")
        return Uri.fromFile(target)
    }

    fun extensionForType(type: String): String = when (type.lowercase()) {
        "image" -> "jpg"
        "audio" -> "m4a"
        "file" -> "dat"
        else -> "bin"
    }
}
