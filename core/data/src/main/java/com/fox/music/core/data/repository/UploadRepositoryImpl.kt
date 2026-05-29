package com.fox.music.core.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadApi: UploadApiService,
) : UploadRepository {

    override suspend fun uploadImage(uri: Uri): Result<String> =
        upload(uri, compressImage = true) { part -> uploadApi.uploadImage(part) }

    override suspend fun uploadAudio(uri: Uri): Result<String> =
        upload(uri, defaultName = "voice_${System.currentTimeMillis()}.m4a") { part ->
            uploadApi.uploadAudio(part)
        }

    override suspend fun uploadVideo(uri: Uri): Result<String> =
        upload(uri, defaultName = "video_${System.currentTimeMillis()}.mp4") { part ->
            uploadApi.uploadVideo(part)
        }

    override suspend fun uploadFile(uri: Uri): Result<String> =
        upload(uri, defaultName = "file_${System.currentTimeMillis()}") { part ->
            uploadApi.uploadFile(part)
        }

    private suspend fun upload(
        uri: Uri,
        compressImage: Boolean = false,
        defaultName: String = "upload_${System.currentTimeMillis()}",
        apiCall: suspend (MultipartBody.Part) -> com.fox.music.core.network.model.ApiResponse<com.fox.music.core.network.model.UploadFileDto>,
    ): Result<String> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val contentResolver = context.contentResolver
        val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取文件")
        val bytes = if (compressImage) ImageCompressor.prepareForUpload(rawBytes) else rawBytes
        val mimeType = contentResolver.getType(uri)
            ?: if (compressImage) "image/jpeg" else "application/octet-stream"
        val fileName = resolveFileName(uri) ?: defaultName
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val response = apiCall(part)
        val url = response.data?.url
        if (response.isSuccess && !url.isNullOrBlank()) {
            url
        } else {
            throw Exception(response.message.ifBlank { "上传失败" })
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        uri.path?.let { path ->
            File(path).name.takeIf { it.isNotBlank() }
        }?.let { return it }
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri))
        return if (!extension.isNullOrBlank()) "upload.$extension" else null
    }
}
