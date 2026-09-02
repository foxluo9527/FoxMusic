package com.fox.music.core.common.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun extractVideoFrameBitmap(
    context: Context,
    previewUrl: String?,
    thumbnailSize: Size = Size(1280, 720),
): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    if (previewUrl.isNullOrBlank()) return@withContext null
    val resolved = MediaUrlResolver.resolve(previewUrl) ?: previewUrl
    val uri = Uri.parse(resolved)
    val localThumb = if (uri.scheme.equals("content", ignoreCase = true)) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, thumbnailSize, null)
        }.getOrNull()
    } else {
        null
    }
    if (localThumb != null) return@withContext localThumb

    runCatching {
        MediaMetadataRetriever().use { retriever ->
            when {
                uri.scheme.equals("content", ignoreCase = true) ||
                    uri.scheme.equals("file", ignoreCase = true) -> retriever.setDataSource(context, uri)
                resolved.startsWith("http://", ignoreCase = true) ||
                    resolved.startsWith("https://", ignoreCase = true) -> retriever.setDataSource(resolved, emptyMap())
                else -> retriever.setDataSource(resolved)
            }
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        }
    }.getOrNull()
}

suspend fun probeVideoAspectRatio(
    context: Context,
    videoUrl: String?,
    fallback: Float = 9f / 16f,
): Pair<Float, Boolean> = withContext(Dispatchers.IO) {
    if (videoUrl.isNullOrBlank()) return@withContext fallback to false
    val resolved = MediaUrlResolver.resolve(videoUrl) ?: videoUrl
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            val uri = Uri.parse(resolved)
            when {
                uri.scheme.equals("content", ignoreCase = true) ||
                    uri.scheme.equals("file", ignoreCase = true) -> retriever.setDataSource(context, uri)
                resolved.startsWith("http://", ignoreCase = true) ||
                    resolved.startsWith("https://", ignoreCase = true) -> retriever.setDataSource(resolved, emptyMap())
                else -> retriever.setDataSource(resolved)
            }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
                height to width
            } else {
                width to height
            }
            if (displayWidth > 0 && displayHeight > 0) {
                displayWidth.toFloat() / displayHeight.toFloat() to true
            } else {
                fallback to false
            }
        }
    }.getOrElse { fallback to false }
}
