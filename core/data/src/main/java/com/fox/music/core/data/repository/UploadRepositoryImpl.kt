package com.fox.music.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.fox.music.core.common.util.ImageCompressor
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatchingWithParser
import com.fox.music.core.domain.repository.ChatUploadResult
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.network.api.UploadApiService
import com.fox.music.core.network.util.ErrorParser
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadApi: UploadApiService,
) : UploadRepository {

    override suspend fun uploadImage(
        uri: Uri,
        compress: Boolean,
        fileName: String?,
    ): Result<String> = upload(
        uri = uri,
        compressImage = compress,
        preferredFileName = fileName,
    ) { part -> uploadApi.uploadImage(part) }

    override suspend fun uploadAudio(uri: Uri, fileName: String?): Result<String> =
        upload(
            uri = uri,
            defaultName = "voice_${System.currentTimeMillis()}.m4a",
            preferredFileName = fileName,
        ) { part -> uploadApi.uploadAudio(part) }

    override suspend fun uploadVideo(uri: Uri, fileName: String?): Result<String> =
        upload(
            uri = uri,
            defaultName = "video_${System.currentTimeMillis()}.mp4",
            preferredFileName = fileName,
        ) { part -> uploadApi.uploadVideo(part) }

    override suspend fun uploadFile(uri: Uri, fileName: String?): Result<String> =
        upload(
            uri = uri,
            defaultName = "file_${System.currentTimeMillis()}",
            preferredFileName = fileName,
        ) { part -> uploadApi.uploadFile(part) }

    override suspend fun uploadChatFile(uri: Uri, fileName: String?): Result<ChatUploadResult> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val resolvedName = fileName?.takeIf { it.isNotBlank() }
                ?: resolveFileName(uri)
                ?: "file_${System.currentTimeMillis()}"
            val body = createStreamingRequestBody(uri, mimeType)
            val part = MultipartBody.Part.createFormData("file", resolvedName, body)
            val response = uploadApi.uploadFile(part)
            val data = response.data
            if (response.isSuccess && data != null && !data.url.isNullOrBlank()) {
                ChatUploadResult(
                    url = data.url,
                    filename = data.filename?.takeIf { it.isNotBlank() } ?: resolvedName,
                    size = data.size ?: resolveContentLength(uri).coerceAtLeast(0L),
                )
            } else {
                throw Exception(response.message.ifBlank { "上传失败" })
            }
        }

    private suspend fun upload(
        uri: Uri,
        compressImage: Boolean = false,
        defaultName: String = "upload_${System.currentTimeMillis()}",
        preferredFileName: String? = null,
        apiCall: suspend (MultipartBody.Part) -> com.fox.music.core.network.model.ApiResponse<com.fox.music.core.network.model.UploadFileDto>,
    ): Result<String> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
            ?: if (compressImage) "image/jpeg" else "application/octet-stream"
        val fileName = preferredFileName?.takeIf { it.isNotBlank() }
            ?: resolveFileName(uri)
            ?: defaultName
        val requestBody = if (compressImage) {
            val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("无法读取文件")
            ImageCompressor.prepareForUpload(rawBytes).toRequestBody(mimeType.toMediaTypeOrNull())
        } else {
            createStreamingRequestBody(uri, mimeType)
        }
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val response = apiCall(part)
        val url = response.data?.url
        if (response.isSuccess && !url.isNullOrBlank()) {
            url
        } else {
            throw Exception(response.message.ifBlank { "上传失败" })
        }
    }

    private fun createStreamingRequestBody(uri: Uri, mimeType: String): RequestBody {
        val mediaType = mimeType.toMediaTypeOrNull()
        val contentLength = resolveContentLength(uri)
        return object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = contentLength

            override fun writeTo(sink: okio.BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw IllegalArgumentException("无法读取文件")
            }
        }
    }

    private fun resolveContentLength(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(index).takeIf { it > 0L } ?: -1L
            }
        }
        if (uri.scheme == "file") {
            uri.path?.let { File(it).length().takeIf { size -> size > 0L } }?.let { return it }
        }
        return -1L
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
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
