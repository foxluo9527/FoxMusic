package com.fox.music.feature.playlist.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File

data class CroppedPlaylistCover(
    val uri: Uri,
    val file: File,
)

/**
 * 以 1:1 比例裁剪图片，输出 JPEG 到应用缓存目录。
 */
class PlaylistImageCropContract : ActivityResultContract<Uri, Uri?>() {

    override fun createIntent(context: Context, input: Uri): Intent {
        val outputFile = File(
            context.cacheDir,
            "playlist_cover_crop_${System.currentTimeMillis()}.jpg",
        )
        val destination = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile,
        )
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(false)
            setShowCropGrid(true)
            setHideBottomControls(false)
        }
        return UCrop.of(input, destination)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1080, 1080)
            .withOptions(options)
            .getIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return null
        }
        return UCrop.getOutput(intent)
    }
}

fun resolveCroppedCoverFile(context: Context, uri: Uri): File? {
    val fileName = uri.lastPathSegment ?: return null
    val file = File(context.cacheDir, fileName)
    return file.takeIf { it.exists() }
}

fun toCroppedPlaylistCover(context: Context, uri: Uri): CroppedPlaylistCover? {
    val file = resolveCroppedCoverFile(context, uri) ?: return null
    return CroppedPlaylistCover(uri = uri, file = file)
}
