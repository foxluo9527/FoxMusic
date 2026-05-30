package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.common.result.Result

data class ChatUploadResult(
    val url: String,
    val filename: String,
    val size: Long,
)

interface UploadRepository {

    suspend fun uploadImage(uri: Uri, compress: Boolean = true, fileName: String? = null): Result<String>

    suspend fun uploadAudio(uri: Uri, fileName: String? = null): Result<String>

    suspend fun uploadVideo(uri: Uri, fileName: String? = null): Result<String>

    suspend fun uploadFile(uri: Uri, fileName: String? = null): Result<String>

    suspend fun uploadChatFile(uri: Uri, fileName: String? = null): Result<ChatUploadResult>
}
