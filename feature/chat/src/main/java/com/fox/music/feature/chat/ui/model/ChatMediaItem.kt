package com.fox.music.feature.chat.ui.model

import android.net.Uri
import com.fox.music.feature.chat.ui.component.ChatMediaPreviewType

data class ChatMediaItem(
    val uri: Uri,
    val type: ChatMediaPreviewType,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateAddedSec: Long,
)
