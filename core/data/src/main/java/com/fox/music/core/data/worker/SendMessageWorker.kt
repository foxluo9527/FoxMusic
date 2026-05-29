package com.fox.music.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.database.dao.ConversationDao
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.network.api.ChatApiService
import com.fox.music.core.network.model.SendMessageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

@HiltWorker
class SendMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val chatApi: ChatApiService,
    private val uploadRepository: UploadRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        val localId = inputData.getString(KEY_LOCAL_ID) ?: return@withContext androidx.work.ListenableWorker.Result.failure()
        val entity = messageDao.getMessageByLocalId(localId)
            ?: return@withContext androidx.work.ListenableWorker.Result.success()

        if (entity.status == "sent" && entity.serverId != null) {
            return@withContext androidx.work.ListenableWorker.Result.success()
        }

        try {
            var content = entity.content
            if (!entity.localMediaUri.isNullOrBlank()) {
                val uri = Uri.parse(entity.localMediaUri)
                val uploadResult = when (entity.type.lowercase()) {
                    "image" -> uploadRepository.uploadImage(uri)
                    "audio" -> uploadRepository.uploadAudio(uri)
                    "file" -> {
                        val fileName = entity.localMediaFileName.orEmpty()
                        if (isVideoFile(fileName)) {
                            uploadRepository.uploadVideo(uri)
                        } else {
                            uploadRepository.uploadFile(uri)
                        }
                    }
                    else -> uploadRepository.uploadFile(uri)
                }
                if (uploadResult is com.fox.music.core.common.result.Result.Error) {
                    markFailed(localId, entity.conversationId, uploadResult.message ?: "上传失败")
                    return@withContext androidx.work.ListenableWorker.Result.failure()
                }
                content = (uploadResult as com.fox.music.core.common.result.Result.Success).data
                messageDao.updateMessageStatus(
                    localId = localId,
                    status = "sending",
                    content = content,
                    localMediaUri = "",
                )
            }

            val response = chatApi.sendMessage(
                SendMessageRequest(
                    receiverId = entity.receiverId,
                    content = content,
                    type = entity.type,
                ),
            )
            val data = response.data
            if (response.isSuccess && data != null) {
                messageDao.updateMessageStatus(
                    localId = localId,
                    status = "sent",
                    serverId = data.id,
                    content = data.content,
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

    private fun isVideoFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
            lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
    }

    companion object {
        const val KEY_LOCAL_ID = "local_id"
        const val WORK_NAME_PREFIX = "send_message_"
    }
}
