package com.fox.music.feature.chat.ui.util

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChatVideoCompressor {

    suspend fun compressTo720p(
        context: Context,
        inputUri: Uri,
    ): Uri = withContext(Dispatchers.IO) {
        val inputFile = copyToCacheIfNeeded(context, inputUri)
        val outDir = File(context.cacheDir, "chat_video").apply { mkdirs() }
        val outFile = File(outDir, "compressed_${System.currentTimeMillis()}.mp4")

        val command = buildString {
            append("-y ")
            append("-i ${quotePath(inputFile.absolutePath)} ")
            append("-vf scale=-2:720 ")
            append("-c:v libx264 -preset veryfast -crf 28 -maxrate 2M -bufsize 4M ")
            append("-c:a aac -b:a 128k ")
            append("-movflags +faststart ")
            append(quotePath(outFile.absolutePath))
        }
        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode
        if (!ReturnCode.isSuccess(returnCode)) {
            val failReason = session.failStackTrace
            val logs = session.allLogsAsString
            throw IllegalStateException(
                buildString {
                    append("视频压缩失败")
                    if (!failReason.isNullOrBlank()) {
                        append(": ")
                        append(failReason)
                    } else if (!logs.isNullOrBlank()) {
                        append(": ")
                        append(logs.take(200))
                    }
                },
            )
        }
        Uri.fromFile(outFile)
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
        val target = File(context.cacheDir, "chat_video_src_${System.currentTimeMillis()}.$extension")
        source.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }
}
