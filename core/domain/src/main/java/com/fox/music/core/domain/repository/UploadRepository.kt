package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.common.result.Result

interface UploadRepository {

    suspend fun uploadImage(uri: Uri): Result<String>

    suspend fun uploadAudio(uri: Uri): Result<String>

    suspend fun uploadVideo(uri: Uri): Result<String>

    suspend fun uploadFile(uri: Uri): Result<String>
}
