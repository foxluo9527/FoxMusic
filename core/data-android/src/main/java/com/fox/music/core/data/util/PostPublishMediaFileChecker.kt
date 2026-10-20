package com.fox.music.core.data.util

import android.content.Context
import android.net.Uri
import com.fox.music.core.database.entity.PostPublishMediaEntity
import com.fox.music.core.data.postpublish.PostPublishScheduler
import com.fox.music.core.model.social.PostMediaType
import java.io.File

object PostPublishMediaFileChecker {

    fun isLocalMediaMissing(context: Context, media: PostPublishMediaEntity): Boolean {
        if (media.uploadStatus == "remote") return false
        val type = PostPublishScheduler.mediaTypeFromStored(media.mediaType)
        return when (type) {
            PostMediaType.IMAGE -> isUriMissing(context, media.localUri)
            PostMediaType.VIDEO -> isUriMissing(context, media.localUri)
            PostMediaType.LIVE -> {
                isUriMissing(context, media.localUri) || isUriMissing(context, media.localVideoUri)
            }
        }
    }

    fun isFileAccessError(error: Throwable?): Boolean {
        val message = error?.message.orEmpty().lowercase()
        return message.contains("无法读取") ||
            message.contains("不存在") ||
            message.contains("not found") ||
            message.contains("no such file") ||
            message.contains("ENOENT") ||
            message.contains("文件丢失") ||
            error is java.io.FileNotFoundException
    }

    private fun isUriMissing(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return true
        val uri = Uri.parse(uriString)
        return !localUriReadable(context, uri)
    }

    private fun localUriReadable(context: Context, uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path ?: return false
                val file = File(path)
                file.exists() && file.isFile && file.length() > 0L
            }
            "content" -> {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
            else -> false
        }
    }
}
