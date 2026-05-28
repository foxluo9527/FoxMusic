package com.fox.music.feature.profile.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File

object AvatarCropHelper {

    private const val MAX_RESULT_SIZE = 1080
    private const val TOOLBAR_COLOR = 0xFF212121.toInt()
    private const val TOOLBAR_WIDGET_COLOR = 0xFFFFFFFF.toInt()

    fun createCropIntent(context: Context, sourceUri: Uri): Intent {
        val destFile = File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
        val destUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile,
        )
        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            setToolbarTitle("裁剪头像")
            setToolbarColor(TOOLBAR_COLOR)
            setToolbarWidgetColor(TOOLBAR_WIDGET_COLOR)
        }
        return UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(MAX_RESULT_SIZE, MAX_RESULT_SIZE)
            .withOptions(options)
            .getIntent(context)
    }
}
