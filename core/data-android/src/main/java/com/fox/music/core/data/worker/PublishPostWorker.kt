package com.fox.music.core.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.common.result.Result as AppResult
import com.fox.music.core.data.postpublish.PostPublishScheduler
import com.fox.music.core.data.util.PostMediaUploader
import com.fox.music.core.data.util.PostPublishMediaFileChecker
import com.fox.music.core.database.dao.PostPublishJobDao
import com.fox.music.core.database.entity.PostPublishMediaEntity
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class PublishPostWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val postPublishJobDao: PostPublishJobDao,
    private val socialRepository: SocialRepository,
    private val postDraftRepository: PostDraftRepository,
    private val uploadRepository: UploadRepository,
    private val postPublishScheduler: PostPublishScheduler,
) : CoroutineWorker(context, params) {

    private val mediaUploader by lazy {
        PostMediaUploader(applicationContext, uploadRepository)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID)
            ?: return@withContext Result.failure()
        val bundle = postPublishJobDao.getJobWithMedia(jobId)
            ?: return@withContext Result.success()

        val job = bundle.job
        val workId = id.toString()
        if (!job.taskUuid.isNullOrBlank() && job.taskUuid != workId) {
            return@withContext Result.success()
        }

        val skipTracker = SkipTracker()
        val now = System.currentTimeMillis()
        try {
            postPublishJobDao.updateJobStatus(jobId, "uploading", now)
            val uploadedMedia = mutableListOf<PostMedia>()

            for (media in bundle.media.sortedBy { it.sortIndex }) {
                when (media.uploadStatus) {
                    "remote" -> {
                        uploadedMedia.add(media.toRemotePostMedia())
                        continue
                    }
                    "uploaded" -> {
                        val url = media.uploadedUrl ?: media.remoteUrl
                        if (url != null) {
                            uploadedMedia.add(media.toUploadedPostMedia(url, media.uploadedVideoUrl))
                            continue
                        }
                    }
                }

                val type = PostPublishScheduler.mediaTypeFromStored(media.mediaType)
                if (PostPublishMediaFileChecker.isLocalMediaMissing(applicationContext, media)) {
                    skipTracker.record(type)
                    postPublishJobDao.deleteMedia(media.mediaId)
                    continue
                }

                val localUri = Uri.parse(requireNotNull(media.localUri))
                postPublishJobDao.updateMediaStatus(media.mediaId, "uploading")
                val videoUri = media.localVideoUri?.let(Uri::parse)
                when (
                    val uploadResult = mediaUploader.upload(
                        type = type,
                        uri = localUri,
                        videoUri = videoUri,
                    )
                ) {
                    is AppResult.Success -> {
                        val uploaded = uploadResult.data
                        uploadedMedia.add(uploaded)
                        postPublishJobDao.updateMediaUploadResult(
                            mediaId = media.mediaId,
                            status = "uploaded",
                            uploadedUrl = uploaded.url,
                            uploadedVideoUrl = uploaded.videoUrl,
                            errorMessage = null,
                        )
                    }
                    is AppResult.Error -> {
                        if (PostPublishMediaFileChecker.isFileAccessError(uploadResult.exception)) {
                            skipTracker.record(type)
                            postPublishJobDao.deleteMedia(media.mediaId)
                            continue
                        }
                        return@withContext failJob(
                            jobId,
                            uploadResult.exception.message ?: "媒体上传失败",
                            media.mediaId,
                        )
                    }
                    is AppResult.Loading -> Unit
                }
            }

            val hadLocalMedia = bundle.media.any { it.uploadStatus != "remote" }
            if (hadLocalMedia && uploadedMedia.isEmpty()) {
                return@withContext failJob(jobId, "媒体文件丢失，请重新添加后发布")
            }

            postPublishJobDao.updateJobStatus(jobId, "publishing", System.currentTimeMillis())
            val publishContent = job.content.ifBlank { job.title.orEmpty() }
            val editingPostId = job.editingPostId
            val publishResult = if (editingPostId != null) {
                socialRepository.updatePost(
                    id = editingPostId,
                    content = publishContent,
                    media = uploadedMedia,
                    tags = job.tagNames.takeIf { it.isNotEmpty() },
                    tagIds = job.tagIds.takeIf { it.isNotEmpty() },
                    musicId = job.musicId,
                    title = job.title?.takeIf { it.isNotBlank() },
                    status = "published",
                )
            } else {
                socialRepository.createPost(
                    content = publishContent,
                    media = uploadedMedia,
                    tags = job.tagNames,
                    tagIds = job.tagIds,
                    musicId = job.musicId,
                    title = job.title?.takeIf { it.isNotBlank() },
                    status = "published",
                )
            }

            when (publishResult) {
                is AppResult.Success -> {
                    if (job.editingPostId == null) {
                        postDraftRepository.clearDraft()
                    }
                    WorkManager.getInstance(applicationContext)
                        .cancelUniqueWork(PostPublishScheduler.uniqueWorkName(jobId))
                    postPublishJobDao.deleteJob(jobId)
                    EventViewModel.notifyPlazaPostsUpdated()
                    EventViewModel.notifyPostPublishCompleted(
                        isEdit = job.editingPostId != null,
                        skippedCount = skipTracker.count,
                        skippedHasImage = skipTracker.hasImage,
                        skippedHasVideo = skipTracker.hasVideo,
                    )
                    postPublishScheduler.kickNextPendingJob(excludeJobId = jobId)
                    Result.success()
                }
                is AppResult.Error -> failJob(
                    jobId,
                    publishResult.exception.message ?: "发布失败",
                )
                is AppResult.Loading -> Result.success()
            }
        } catch (e: Exception) {
            failJob(jobId, e.message ?: "发布失败")
        }
    }

    private suspend fun failJob(
        jobId: String,
        message: String,
        mediaId: String? = null,
    ): Result {
        if (mediaId != null) {
            postPublishJobDao.updateMediaUploadResult(
                mediaId = mediaId,
                status = "failed",
                uploadedUrl = null,
                uploadedVideoUrl = null,
                errorMessage = message,
            )
        }
        postPublishJobDao.updateJobFailed(
            jobId = jobId,
            status = "failed",
            errorMessage = message,
            updatedAt = System.currentTimeMillis(),
        )
        postPublishScheduler.kickNextPendingJob(excludeJobId = jobId)
        return Result.failure()
    }

    private fun PostPublishMediaEntity.toRemotePostMedia(): PostMedia {
        val type = PostPublishScheduler.mediaTypeFromStored(mediaType)
        val url = remoteUrl.orEmpty()
        return when (type) {
            PostMediaType.VIDEO -> PostMedia(
                type = type,
                url = url,
                videoUrl = remoteVideoUrl ?: url,
            )
            PostMediaType.LIVE -> PostMedia(
                type = type,
                url = url,
                videoUrl = remoteVideoUrl,
            )
            PostMediaType.IMAGE -> PostMedia(type = type, url = url)
        }
    }

    private fun PostPublishMediaEntity.toUploadedPostMedia(
        url: String,
        videoUrl: String?,
    ): PostMedia {
        val type = PostPublishScheduler.mediaTypeFromStored(mediaType)
        return when (type) {
            PostMediaType.VIDEO -> PostMedia(
                type = type,
                url = url,
                videoUrl = videoUrl ?: url,
            )
            PostMediaType.LIVE -> PostMedia(type = type, url = url, videoUrl = videoUrl)
            PostMediaType.IMAGE -> PostMedia(type = type, url = url)
        }
    }

    private class SkipTracker {
        var count: Int = 0
            private set
        var hasImage: Boolean = false
            private set
        var hasVideo: Boolean = false
            private set

        fun record(type: PostMediaType) {
            count++
            when (type) {
                PostMediaType.IMAGE -> hasImage = true
                PostMediaType.VIDEO -> hasVideo = true
                PostMediaType.LIVE -> {
                    hasImage = true
                    hasVideo = true
                }
            }
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val WORK_TAG = "publish_post_worker"
    }
}
