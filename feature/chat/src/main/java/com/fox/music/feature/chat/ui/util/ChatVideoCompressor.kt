package com.fox.music.feature.chat.ui.util

import android.content.Context
import android.net.Uri
import com.fox.music.core.data.util.VideoCompressor

object ChatVideoCompressor {

    suspend fun compressTo720p(
        context: Context,
        inputUri: Uri,
    ): Uri = VideoCompressor.compressTo720p(context, inputUri)
}
