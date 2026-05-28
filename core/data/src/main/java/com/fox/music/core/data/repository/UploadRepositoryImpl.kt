package com.fox.music.core.data.repository

import android.content.Context
import android.net.Uri
import com.fox.music.core.common.util.ImageCompressor
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatchingWithParser
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.network.api.UploadApiService
import com.fox.music.core.network.util.ErrorParser
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadApi: UploadApiService,
) : UploadRepository {

    override suspend fun uploadImage(uri: Uri): Result<String> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val contentResolver = context.contentResolver
            val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("无法读取图片")
            val bytes = ImageCompressor.prepareForUpload(rawBytes)
            val mimeType = "image/jpeg"
            val fileName = "cover_${System.currentTimeMillis()}.jpg"
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val response = uploadApi.uploadImage(part)
            val url = response.data?.url
            if (response.isSuccess && !url.isNullOrBlank()) {
                url
            } else {
                throw Exception(response.message.ifBlank { "上传失败" })
            }
        }
}
