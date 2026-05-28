package com.fox.music.core.common.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageCompressor {

    const val MAX_IMAGE_SIZE_BYTES: Int = 2 * 1024 * 1024

    /**
     * 将图片居中裁剪为 1:1，并压缩到 [maxSizeBytes] 以内（默认 2MB）。
     */
    fun prepareForUpload(
        input: ByteArray,
        maxSizeBytes: Int = MAX_IMAGE_SIZE_BYTES,
    ): ByteArray {
        val decoded = BitmapFactory.decodeByteArray(input, 0, input.size)
            ?: throw IllegalArgumentException("无法解码图片")
        val squared = cropCenterSquare(decoded)
        if (squared !== decoded) {
            decoded.recycle()
        }
        return compressToMaxSize(squared, maxSizeBytes).also { squared.recycle() }
    }

    fun compressToMaxSize(
        bitmap: Bitmap,
        maxSizeBytes: Int = MAX_IMAGE_SIZE_BYTES,
    ): ByteArray {
        var working = bitmap
        var createdScaled = false
        var quality = 90
        var result = encodeJpeg(working, quality)

        while (result.size > maxSizeBytes && quality > 20) {
            quality -= 10
            result = encodeJpeg(working, quality)
        }

        var currentMaxSide = max(working.width, working.height)
        while (result.size > maxSizeBytes && currentMaxSide > 320) {
            currentMaxSide = (currentMaxSide * 0.85f).roundToInt().coerceAtLeast(320)
            val scale = currentMaxSide.toFloat() / max(working.width, working.height)
            val targetWidth = max(1, (working.width * scale).roundToInt())
            val targetHeight = max(1, (working.height * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(working, targetWidth, targetHeight, true)
            if (createdScaled) {
                working.recycle()
            }
            working = scaled
            createdScaled = true
            quality = 85
            result = encodeJpeg(working, quality)
            while (result.size > maxSizeBytes && quality > 20) {
                quality -= 10
                result = encodeJpeg(working, quality)
            }
        }

        if (createdScaled) {
            working.recycle()
        }
        return result
    }

    private fun cropCenterSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        if (bitmap.width == size && bitmap.height == size) {
            return bitmap
        }
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)) {
            throw IllegalStateException("图片压缩失败")
        }
        return outputStream.toByteArray()
    }
}
