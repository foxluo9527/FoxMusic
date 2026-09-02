package com.fox.music.core.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.postpublish.PostPublishScheduler
import com.fox.music.core.database.dao.PostPublishJobDao
import com.fox.music.core.database.entity.PostPublishJobEntity
import com.fox.music.core.database.entity.PostPublishMediaEntity
import com.fox.music.core.domain.repository.EnqueuePostPublishMedia
import com.fox.music.core.domain.repository.PostPublishJob
import com.fox.music.core.domain.repository.PostPublishJobStatus
import com.fox.music.core.domain.repository.PostPublishMediaItem
import com.fox.music.core.domain.repository.PostPublishRepository
import com.fox.music.core.model.social.PostMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PostPublishRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postPublishJobDao: PostPublishJobDao,
    private val postPublishScheduler: PostPublishScheduler,
) : PostPublishRepository {

    override fun observeActiveJobs(): Flow<List<PostPublishJob>> =
        postPublishJobDao.observeActiveJobs().flatMapLatest { jobs ->
            flow {
                emit(
                    jobs.map { job ->
                        job.toDomain(postPublishJobDao.getMediaForJob(job.jobId))
                    },
                )
            }
        }

    override suspend fun enqueuePublish(
        title: String?,
        content: String,
        media: List<EnqueuePostPublishMedia>,
        tagNames: List<String>,
        tagIds: List<Long>,
        musicId: Long?,
        editingPostId: Long?,
    ): Result<String> = suspendRunCatching {
        postPublishScheduler.enqueue(
            title = title,
            content = content,
            media = media,
            tagNames = tagNames,
            tagIds = tagIds,
            musicId = musicId,
            editingPostId = editingPostId,
        )
    }

    override suspend fun retryJob(jobId: String): Result<Unit> = suspendRunCatching {
        val job = postPublishJobDao.getJob(jobId) ?: throw IllegalArgumentException("任务不存在")
        if (job.status != "failed") {
            throw IllegalStateException("仅失败任务可重试")
        }
        postPublishScheduler.retry(jobId)
    }

    override suspend fun cancelJob(jobId: String): Result<Unit> = suspendRunCatching {
        WorkManager.getInstance(context)
            .cancelUniqueWork(PostPublishScheduler.uniqueWorkName(jobId))
        postPublishJobDao.deleteJob(jobId)
    }

    override suspend fun ensureWorkerRunning() {
        postPublishScheduler.kickNextPendingJob()
    }

    private fun PostPublishJobEntity.toDomain(
        mediaEntities: List<PostPublishMediaEntity>,
    ): PostPublishJob = PostPublishJob(
        jobId = jobId,
        editingPostId = editingPostId,
        title = title,
        content = content,
        tagNames = tagNames,
        tagIds = tagIds,
        musicId = musicId,
        status = status.toJobStatus(),
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mediaItems = mediaEntities.map { it.toDomain() },
    )

    private fun PostPublishMediaEntity.toDomain() = PostPublishMediaItem(
        mediaId = mediaId,
        sortIndex = sortIndex,
        type = when (mediaType.lowercase()) {
            "video" -> PostMediaType.VIDEO
            "live" -> PostMediaType.LIVE
            else -> PostMediaType.IMAGE
        },
        localUri = localUri,
        localVideoUri = localVideoUri,
        remoteUrl = remoteUrl,
        remoteVideoUrl = remoteVideoUrl,
        uploadStatus = uploadStatus,
        uploadedUrl = uploadedUrl,
        uploadedVideoUrl = uploadedVideoUrl,
        errorMessage = errorMessage,
    )

    private fun String.toJobStatus(): PostPublishJobStatus = when (lowercase()) {
        "uploading" -> PostPublishJobStatus.UPLOADING
        "publishing" -> PostPublishJobStatus.PUBLISHING
        "failed" -> PostPublishJobStatus.FAILED
        else -> PostPublishJobStatus.QUEUED
    }
}
