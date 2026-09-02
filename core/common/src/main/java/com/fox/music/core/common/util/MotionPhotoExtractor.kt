package com.fox.music.core.common.util

import android.content.Context
import android.net.Uri
import androidx.collection.LruCache
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MotionPhotoResult(
    val stillUri: Uri,
    val videoUri: Uri,
)

/**
 * 尝试从 Google/Samsung Motion Photo（JPEG 内嵌 MicroVideo）提取短视频。
 * 失败返回 null，调用方应降级为普通图片。
 */
object MotionPhotoExtractor {

    private val motionPhotoCache = LruCache<String, Boolean>(128)

    suspend fun isMotionPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val cacheKey = uri.toString()
        motionPhotoCache.get(cacheKey)?.let { return@withContext it }
        val detected = runCatching { detectsMotionPhoto(context, uri) }.getOrDefault(false)
        motionPhotoCache.put(cacheKey, detected)
        detected
    }

    suspend fun extract(context: Context, uri: Uri): MotionPhotoResult? = withContext(Dispatchers.IO) {
        val bytes = readAllBytes(context, uri) ?: return@withContext null
        val offset = findVideoOffset(bytes) ?: return@withContext null
        if (offset <= 0 || offset >= bytes.size) return@withContext null

        val outDir = File(context.cacheDir, "motion_photo").apply { mkdirs() }
        val stillFile = File(outDir, "still_${System.currentTimeMillis()}.jpg")
        val videoFile = File(outDir, "motion_${System.currentTimeMillis()}.mp4")
        stillFile.writeBytes(bytes.copyOfRange(0, offset))
        videoFile.writeBytes(bytes.copyOfRange(offset, bytes.size))
        if (videoFile.length() < 1024) return@withContext null
        motionPhotoCache.put(uri.toString(), true)
        MotionPhotoResult(
            stillUri = Uri.fromFile(stillFile),
            videoUri = Uri.fromFile(videoFile),
        )
    }

    private fun detectsMotionPhoto(context: Context, uri: Uri): Boolean {
        val bytes = readAllBytes(context, uri) ?: return false
        val offset = findVideoOffset(bytes) ?: return false
        return offset > 0 && offset < bytes.size
    }

    private fun readAllBytes(context: Context, uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    private fun findVideoOffset(bytes: ByteArray): Int? {
        val text = bytes.toString(Charset.forName("ISO-8859-1"))
        val patterns = listOf(
            Regex("""MicroVideoOffset["\s:=]+(\d+)"""),
            Regex("""MotionPhotoOffset["\s:=]+(\d+)"""),
            Regex("""Item:Length=["']?(\d+)["']?[^>]*Item:Semantic=["']MotionPhoto["']"""),
        )
        for (pattern in patterns) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { offset ->
                if (pattern.pattern.contains("MicroVideoOffset") || pattern.pattern.contains("MotionPhotoOffset")) {
                    val fromEnd = bytes.size - offset
                    if (fromEnd > 0) return fromEnd
                }
                return offset
            }
        }
        return findFtypOffset(bytes, 0)
    }

    private fun findFtypOffset(bytes: ByteArray, baseOffset: Int): Int? {
        val altMagic = "ftyp".toByteArray()
        val searchStart = (bytes.size * 0.4).toInt()
        for (i in searchStart until bytes.size - 8) {
            if (bytes[i + 4] == altMagic[0] &&
                bytes[i + 5] == altMagic[1] &&
                bytes[i + 6] == altMagic[2] &&
                bytes[i + 7] == altMagic[3]
            ) {
                return baseOffset + i
            }
        }
        return null
    }
}
