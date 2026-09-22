package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.model.social.PostMediaType
import kotlinx.coroutines.flow.Flow

enum class PostPublishJobStatus {
    QUEUED,
    UPLOADING,
    PUBLISHING,
    FAILED,
}

data class PostPublishMediaItem(
    val mediaId: String,
    val sortIndex: Int,
    val type: PostMediaType,
    val localUri: String?,
    val localVideoUri: String?,
    val remoteUrl: String?,
    val remoteVideoUrl: String?,
    val uploadStatus: String,
    val uploadedUrl: String?,
    val uploadedVideoUrl: String?,
    val errorMessage: String?,
)

data class PostPublishJob(
    val jobId: String,
    val editingPostId: Long?,
    val title: String?,
    val content: String,
    val tagNames: List<String>,
    val tagIds: List<Long>,
    val musicId: Long?,
    val status: PostPublishJobStatus,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val mediaItems: List<PostPublishMediaItem>,
) {
    val totalLocalUploadCount: Int
        get() = mediaItems.count { it.localUri != null }

    val uploadedLocalCount: Int
        get() = mediaItems.count {
            it.localUri != null && (it.uploadStatus == "uploaded" || it.uploadStatus == "remote")
        }
}

data class EnqueuePostPublishMedia(
    val type: PostMediaType,
    val uri: Uri? = null,
    val videoUri: Uri? = null,
    val remoteUrl: String? = null,
    val remoteVideoUrl: String? = null,
)

interface PostPublishRepository {

    fun observeActiveJobs(): Flow<List<PostPublishJob>>

    suspend fun enqueuePublish(
        title: String?,
        content: String,
        media: List<EnqueuePostPublishMedia>,
        tagNames: List<String>,
        tagIds: List<Long>,
        musicId: Long?,
        editingPostId: Long? = null,
    ): Result<String>

    suspend fun retryJob(jobId: String): Result<Unit>

    suspend fun cancelJob(jobId: String): Result<Unit>

    /** 确保排队中的任务已调度 Worker（应用恢复或队列页打开时调用）。 */
    suspend fun ensureWorkerRunning()
}
