package com.fox.music.core.data.worker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fox.music.core.common.result.Result as AppResult
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.data.util.ChatMediaStorage
import com.fox.music.core.data.util.ChatUploadPolicy
import com.fox.music.core.database.dao.ConversationDao
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.domain.repository.ChatUploadResult
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.network.api.ChatApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class SendMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val chatApi: ChatApiService,
    private val uploadRepository: UploadRepository,
    private val chatMediaStorage: ChatMediaStorage,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        val localId = inputData.getString(KEY_LOCAL_ID)
            ?: return@withContext androidx.work.ListenableWorker.Result.failure()
        val entity = messageDao.getMessageByLocalId(localId)
            ?: return@withContext androidx.work.ListenableWorker.Result.success()

        if (entity.status == "sent" && entity.serverId != null) {
            return@withContext androidx.work.ListenableWorker.Result.success()
        }

        val workId = id.toString()
        if (!entity.taskUuid.isNullOrBlank() && entity.taskUuid != workId) {
            return@withContext androidx.work.ListenableWorker.Result.success()
        }

        try {
            val mediaPayload = resolveMediaPayload(entity)
            if (mediaPayload is MediaPayload.Error) {
                markFailed(localId, entity.conversationId, mediaPayload.message)
                return@withContext androidx.work.ListenableWorker.Result.failure()
            }

            val payload = mediaPayload as? MediaPayload.Ready
            if (payload != null) {
                messageDao.updateMessageStatus(
                    localId = localId,
                    status = "sending",
                    remoteMediaUrl = payload.fileUrl,
                    uploadedAt = payload.uploadedAt,
                    fileType = payload.fileType,
                )
            }

            val request = buildSendRequest(entity, payload)
            val response = chatApi.sendMessage(request)
            val data = response.data
            if (response.isSuccess && data != null) {
                messageDao.updateMessageStatus(
                    localId = localId,
                    status = "sent",
                    serverId = data.id,
                    content = data.content,
                    remoteMediaUrl = data.fileUrl ?: payload?.fileUrl,
                    fileType = data.fileType ?: payload?.fileType ?: entity.fileType,
                    errorMessage = null,
                )
                conversationDao.updateLastMessage(
                    peerUserId = entity.conversationId,
                    preview = previewForMessage(data.content, data.type),
                    status = "sent",
                    at = System.currentTimeMillis(),
                    localId = localId,
                )
                androidx.work.ListenableWorker.Result.success()
            } else {
                markFailed(localId, entity.conversationId, response.message.ifBlank { "发送失败" })
                androidx.work.ListenableWorker.Result.failure()
            }
        } catch (e: Exception) {
            markFailed(localId, entity.conversationId, e.message ?: "发送失败")
            androidx.work.ListenableWorker.Result.failure()
        }
    }

    private suspend fun resolveMediaPayload(entity: MessageEntity): MediaPayload {
        val type = entity.type.lowercase()
        if (type !in MEDIA_TYPES) return MediaPayload.None

        val cachedRemote = entity.remoteMediaUrl?.takeIf { isUploadedUrl(it) }
            ?: entity.content.takeIf { isUploadedUrl(it) }
        if (cachedRemote != null && canReuseUploadedUrl(cachedRemote, entity.uploadedAt)) {
            return MediaPayload.Ready(
                fileUrl = cachedRemote,
                fileName = resolveFileName(entity, null),
                fileSize = 0L,
                uploadedAt = entity.uploadedAt ?: System.currentTimeMillis(),
                fileType = entity.fileType ?: resolveFileType(resolveFileName(entity, null)),
            )
        }

        if (!entity.localMediaUri.isNullOrBlank()) {
            val uploadUri = ensureLocalUploadUri(entity)
            val uploadResult = uploadMedia(uploadUri, entity.localMediaFileName)
            if (uploadResult is AppResult.Error) {
                return MediaPayload.Error(uploadResult.message ?: "上传失败")
            }
            val uploaded = uploadResult.getOrNull() ?: return MediaPayload.Error("上传失败")
            val uploadedAt = System.currentTimeMillis()
            val fileName = uploaded.filename.substringAfterLast('/').ifBlank { resolveFileName(entity, uploadUri) }
            val fileType = entity.fileType ?: resolveFileType(fileName)
            return MediaPayload.Ready(
                fileUrl = uploaded.url,
                fileName = fileName,
                fileSize = uploaded.size.coerceAtLeast(0L),
                uploadedAt = uploadedAt,
                fileType = fileType,
            )
        }

        return MediaPayload.Error("媒体文件不可用")
    }

    private suspend fun ensureLocalUploadUri(entity: MessageEntity): Uri {
        val rawUri = Uri.parse(entity.localMediaUri!!)
        if (rawUri.scheme == "file") return rawUri
        val extension = chatMediaStorage.extensionForFile(entity.localMediaFileName, entity.type)
        val persisted = chatMediaStorage.persistUri(rawUri, extension)
        messageDao.updateMessageStatus(
            localId = entity.localId,
            status = "sending",
            localMediaUri = persisted.toString(),
        )
        return persisted
    }

    private suspend fun uploadMedia(
        uploadUri: Uri,
        fileName: String?,
    ): AppResult<ChatUploadResult> = uploadRepository.uploadChatFile(uploadUri, fileName)

    private fun buildSendRequest(
        entity: MessageEntity,
        payload: MediaPayload.Ready?,
    ): Map<String, String> = when (entity.type.lowercase()) {
        "file", "image" -> buildFileMessageRequest(entity, payload)
        "voice", "audio" -> buildVoiceMessageRequest(entity, payload)
        else -> mapOf(
            "receiverId" to entity.receiverId.toString(),
            "content" to entity.content,
            "type" to "text",
        )
    }

    private fun buildFileMessageRequest(
        entity: MessageEntity,
        payload: MediaPayload.Ready?,
    ): Map<String, String> {
        val name = payload?.fileName.orEmpty().ifBlank { resolveFileName(entity, null) }
        val fileType = payload?.fileType ?: entity.fileType ?: resolveFileType(name)
        return mapOf(
            "receiverId" to entity.receiverId.toString(),
            "content" to "[file]$name",
            "type" to "file",
            "file_url" to (payload?.fileUrl ?: entity.remoteMediaUrl.orEmpty()),
            "file_type" to fileType,
            "file_name" to name,
            "file_size" to payload?.fileSize?.coerceAtLeast(0L)?.toString().orEmpty(),
        )
    }

    private fun buildVoiceMessageRequest(
        entity: MessageEntity,
        payload: MediaPayload.Ready?,
    ): Map<String, String> {
        val durationSec = ((entity.audioDurationMs ?: 0L) / 1000L).coerceAtLeast(1L)
        return mapOf(
            "receiverId" to entity.receiverId.toString(),
            "content" to "/",
            "type" to "text",
            "voice_url" to (payload?.fileUrl ?: entity.remoteMediaUrl.orEmpty()),
            "voice_duration" to durationSec.toString(),
        )
    }

    private fun resolveFileName(entity: MessageEntity, uri: Uri?): String {
        entity.localMediaFileName?.takeIf { it.isNotBlank() }?.let { return it }
        uri?.let { resolveDisplayName(it) }?.takeIf { it.isNotBlank() }?.let { return it }
        entity.content.takeIf { it.startsWith("[file]", ignoreCase = true) }?.let {
            return it.removePrefix("[file]").ifBlank { "file" }
        }
        if (isUploadedUrl(entity.content)) {
            return entity.content.substringAfterLast('/').ifBlank { "file" }
        }
        return "file_${System.currentTimeMillis()}"
    }

    private fun resolveDisplayName(uri: Uri): String? {
        applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        uri.path?.let { path ->
            File(path).name.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun resolveContentLength(uri: Uri): Long {
        applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getLong(index).takeIf { it > 0L } ?: 0L
                }
            }
        if (uri.scheme == "file") {
            uri.path?.let { File(it).length().takeIf { size -> size > 0L } }?.let { return it }
        }
        return 0L
    }

    private fun resolveFileType(fileName: String): String = when {
        isImageFile(fileName) -> "image"
        isVideoFile(fileName) -> "video"
        else -> "file"
    }

    private fun isUploadedUrl(value: String): Boolean =
        isRemoteUrl(value) || value.startsWith("/uploads/")

    private fun canReuseUploadedUrl(url: String, uploadedAt: Long?): Boolean {
        if (!ChatUploadPolicy.isUploadStillValid(uploadedAt)) return false
        return !url.contains("/uploads/temp/", ignoreCase = true)
    }

    private fun isRemoteUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun isImageFile(value: String): Boolean {
        val lower = value.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
    }

    private fun isVideoFile(value: String): Boolean {
        val lower = value.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
            lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
    }

    private suspend fun markFailed(localId: String, conversationId: Long, error: String) {
        messageDao.updateMessageStatus(
            localId = localId,
            status = "failed",
            errorMessage = error,
        )
        val entity = messageDao.getMessageByLocalId(localId) ?: return
        conversationDao.updateLastMessage(
            peerUserId = conversationId,
            preview = previewForMessage(entity.content, entity.type),
            status = "failed",
            at = System.currentTimeMillis(),
            localId = localId,
        )
    }

    private sealed interface MediaPayload {
        data object None : MediaPayload
        data class Ready(
            val fileUrl: String,
            val fileName: String,
            val fileSize: Long,
            val uploadedAt: Long,
            val fileType: String,
        ) : MediaPayload
        data class Error(val message: String) : MediaPayload
    }

    companion object {
        const val KEY_LOCAL_ID = "local_id"
        const val WORK_NAME_PREFIX = "send_message_"
        private val MEDIA_TYPES = setOf("image", "audio", "voice", "file", "video")
    }
}
