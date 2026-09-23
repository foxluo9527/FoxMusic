package com.fox.music.feature.chat.ui.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ChatCameraHelper {

    fun createImageUri(context: Context): Uri {
        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun createVideoUri(context: Context): Uri {
        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.mp4")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
