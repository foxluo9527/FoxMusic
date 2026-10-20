package com.fox.music.core.data.postpublish

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fox.music.core.data.worker.PublishPostWorker
import com.fox.music.core.database.dao.PostPublishJobDao
import com.fox.music.core.database.entity.PostPublishJobEntity
import com.fox.music.core.database.entity.PostPublishMediaEntity
import com.fox.music.core.domain.repository.EnqueuePostPublishMedia
import com.fox.music.core.model.social.PostMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostPublishScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postPublishJobDao: PostPublishJobDao,
) {

    suspend fun enqueue(
        title: String?,
        content: String,
        media: List<EnqueuePostPublishMedia>,
        tagNames: List<String>,
        tagIds: List<Long>,
        musicId: Long?,
        editingPostId: Long?,
    ): String {
        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()
        postPublishJobDao.insertJob(
            PostPublishJobEntity(
                jobId = jobId,
                editingPostId = editingPostId,
                title = title,
                content = content,
                tagNames = tagNames,
                tagIds = tagIds,
                musicId = musicId,
                status = "queued",
                createdAt = now,
                updatedAt = now,
            ),
        )
        val mediaEntities = media.mapIndexed { index, item ->
            val isRemoteOnly = !item.remoteUrl.isNullOrBlank()
            PostPublishMediaEntity(
                mediaId = UUID.randomUUID().toString(),
                jobId = jobId,
                sortIndex = index,
                mediaType = item.type.name.lowercase(),
                localUri = if (isRemoteOnly) null else item.uri?.toString(),
                localVideoUri = if (isRemoteOnly) null else item.videoUri?.toString(),
                remoteUrl = item.remoteUrl,
                remoteVideoUrl = item.remoteVideoUrl,
                uploadStatus = if (isRemoteOnly) "remote" else "pending",
            )
        }
        postPublishJobDao.insertMedia(mediaEntities)
        kickWorker(jobId)
        return jobId
    }

    suspend fun retry(jobId: String) {
        val now = System.currentTimeMillis()
        postPublishJobDao.resetLocalMediaUploads(jobId)
        postPublishJobDao.updateJobStatus(jobId, "queued", now)
        kickWorker(jobId)
    }

    suspend fun kickWorker(jobId: String? = null) {
        val targetJobId = jobId ?: postPublishJobDao.getNextRunnableJob()?.jobId ?: return
        val workId = UUID.randomUUID()
        postPublishJobDao.updateTaskUuid(targetJobId, workId.toString(), System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<PublishPostWorker>()
            .setId(workId)
            .setInputData(
                Data.Builder()
                    .putString(PublishPostWorker.KEY_JOB_ID, targetJobId)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(PublishPostWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                uniqueWorkName(targetJobId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }

    suspend fun kickNextPendingJob(excludeJobId: String? = null) {
        val next = postPublishJobDao.getActiveJobs()
            .sortedBy { it.createdAt }
            .firstOrNull { it.jobId != excludeJobId && it.status != "failed" }
            ?: postPublishJobDao.getActiveJobs()
                .sortedBy { it.createdAt }
                .firstOrNull { it.jobId != excludeJobId }
        if (next != null) {
            kickWorker(next.jobId)
        }
    }

    companion object {
        fun uniqueWorkName(jobId: String) = "publish_post_$jobId"

        fun mediaTypeFromStored(value: String): PostMediaType = when (value.lowercase()) {
            "video" -> PostMediaType.VIDEO
            "live" -> PostMediaType.LIVE
            else -> PostMediaType.IMAGE
        }
    }
}
