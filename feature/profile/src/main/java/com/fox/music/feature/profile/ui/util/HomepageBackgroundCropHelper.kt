package com.fox.music.feature.profile.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File

object HomepageBackgroundCropHelper {

    private const val TOOLBAR_COLOR = 0xFF212121.toInt()
    private const val TOOLBAR_WIDGET_COLOR = 0xFFFFFFFF.toInt()

    fun createCropIntent(context: Context, sourceUri: Uri): Intent {
        val destFile = File(
            context.cacheDir,
            "cropped_homepage_bg_${System.currentTimeMillis()}.jpg",
        )
        val destUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile,
        )
        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            setToolbarTitle("裁剪封面")
            setToolbarColor(TOOLBAR_COLOR)
            setToolbarWidgetColor(TOOLBAR_WIDGET_COLOR)
        }
        return UCrop.of(sourceUri, destUri)
            .withAspectRatio(
                UserHomeCoverSpec.MAX_RESULT_WIDTH.toFloat(),
                UserHomeCoverSpec.MAX_RESULT_HEIGHT.toFloat(),
            )
            .withMaxResultSize(
                UserHomeCoverSpec.MAX_RESULT_WIDTH,
                UserHomeCoverSpec.MAX_RESULT_HEIGHT,
            )
            .withOptions(options)
            .getIntent(context)
    }
}
