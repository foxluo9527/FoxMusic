package com.fox.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.net.toUri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fox.music.core.common.util.MediaUrlResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetCoverCache {
    private const val SUBDIR = "widget_covers"

    suspend fun resolveLocalUri(context: Context, coverUrl: String?): Uri? {
        val path = resolveLocalPath(context, coverUrl) ?: return null
        return File(path).toUri()
    }

    suspend fun resolveLocalPath(context: Context, coverUrl: String?): String? {
        if (coverUrl.isNullOrBlank()) return null
        val cacheFile = cacheFileFor(coverUrl)
        val cached = File(context.cacheDir, "$SUBDIR/${cacheFile.name}")
        if (cached.exists() && cached.length() > 0L) {
            return cached.absolutePath
        }
        val resolved = MediaUrlResolver.resolve(coverUrl) ?: return null
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(resolved)
                .allowHardware(false)
                .build(),
        )
        if (result !is SuccessResult) return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        return withContext(Dispatchers.IO) {
            cached.parentFile?.mkdirs()
            cached.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            cached.absolutePath
        }
    }

    private fun cacheFileFor(coverUrl: String): File =
        File("$SUBDIR/${coverUrl.hashCode()}.jpg")
}
