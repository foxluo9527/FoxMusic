package com.fox.music.core.data.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Log
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoCompressor {

    /**
     * 上传前压缩：最高 1080p（不放大），CRF 23 + 5Mbps 上限。
     */
    suspend fun compressTo720p(
        context: Context,
        inputUri: Uri,
    ): Uri = withContext(Dispatchers.IO) {
        val inputFile = copyToCacheIfNeeded(context, inputUri)
        val outDir = File(context.cacheDir, "compressed_video").apply { mkdirs() }
        val outFile = File(outDir, "compressed_${System.currentTimeMillis()}.mp4")

        val scaleFilter = resolveScaleFilter(inputFile)
        val command = buildString {
            append("-y ")
            append("-i ${quotePath(inputFile.absolutePath)} ")
            if (scaleFilter != null) {
                append("-vf $scaleFilter ")
            }
            append("-c:v libx264 -preset fast -crf 23 -maxrate 5M -bufsize 10M ")
            append("-c:a aac -b:a 128k ")
            append("-movflags +faststart ")
            append(quotePath(outFile.absolutePath))
        }
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw IllegalStateException("视频压缩失败${extractFfmpegError(session)?.let { ": $it" }.orEmpty()}")
        }
        if (!outFile.exists() || outFile.length() <= 0L) {
            throw IllegalStateException("视频压缩失败: 输出文件无效")
        }
        Uri.fromFile(outFile)
    }

    /** 仅当超过 1080p 时才缩小，避免低分辨率被放大变糊。 */
    private fun resolveScaleFilter(inputFile: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(inputFile.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return "scale=-2:1080"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return "scale=-2:1080"
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
                height to width
            } else {
                width to height
            }
            when {
                displayHeight > 1080 -> "scale=-2:1080"
                displayWidth > 1920 -> "scale=1920:-2"
                else -> null
            }
        } catch (_: Exception) {
            "scale=-2:1080"
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFfmpegError(session: Session): String? {
        session.failStackTrace?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(300) }
        val errorLogs = session.allLogs
            ?.mapNotNull(Log::getMessage)
            ?.filter { line ->
                val lower = line.lowercase()
                lower.contains("error") ||
                    lower.contains("invalid") ||
                    lower.contains("no such filter") ||
                    lower.contains("failed")
            }
            ?.distinct()
            ?.joinToString("; ")
            ?.trim()
        if (!errorLogs.isNullOrBlank()) return errorLogs.take(300)
        return session.output?.trim()?.lines()?.lastOrNull { it.isNotBlank() }?.take(300)
    }

    private fun quotePath(path: String): String = "'${path.replace("'", "'\\''")}'"

    private fun copyToCacheIfNeeded(context: Context, inputUri: Uri): File {
        if (inputUri.scheme == "file") {
            return File(requireNotNull(inputUri.path) { "无效视频路径" })
        }
        val source = context.contentResolver.openInputStream(inputUri)
            ?: throw IllegalStateException("无法读取视频文件")
        val extension = context.contentResolver.getType(inputUri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "mp4"
        val target = File(context.cacheDir, "video_src_${System.currentTimeMillis()}.$extension")
        source.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }
}
