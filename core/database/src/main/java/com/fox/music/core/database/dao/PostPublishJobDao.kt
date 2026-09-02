package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fox.music.core.database.entity.PostPublishJobEntity
import com.fox.music.core.database.entity.PostPublishMediaEntity
import kotlinx.coroutines.flow.Flow

data class PostPublishJobWithMedia(
    val job: PostPublishJobEntity,
    val media: List<PostPublishMediaEntity>,
)

@Dao
interface PostPublishJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: PostPublishJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(items: List<PostPublishMediaEntity>)

    @Query("SELECT * FROM post_publish_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getJob(jobId: String): PostPublishJobEntity?

    @Query("SELECT * FROM post_publish_media WHERE jobId = :jobId ORDER BY sortIndex ASC")
    suspend fun getMediaForJob(jobId: String): List<PostPublishMediaEntity>

    @Transaction
    suspend fun getJobWithMedia(jobId: String): PostPublishJobWithMedia? {
        val job = getJob(jobId) ?: return null
        return PostPublishJobWithMedia(job, getMediaForJob(jobId))
    }

    @Query(
        """
        SELECT * FROM post_publish_jobs
        WHERE status IN ('queued', 'uploading', 'publishing', 'failed')
        ORDER BY createdAt ASC
        """,
    )
    fun observeActiveJobs(): Flow<List<PostPublishJobEntity>>

    @Query("SELECT * FROM post_publish_media WHERE jobId = :jobId ORDER BY sortIndex ASC")
    fun observeMediaForJob(jobId: String): Flow<List<PostPublishMediaEntity>>

    @Query(
        """
        SELECT * FROM post_publish_jobs
        WHERE status IN ('queued', 'uploading', 'publishing', 'failed')
        ORDER BY createdAt DESC
        """,
    )
    suspend fun getActiveJobs(): List<PostPublishJobEntity>

    @Query(
        """
        SELECT * FROM post_publish_jobs
        WHERE status IN ('queued', 'uploading', 'publishing', 'failed')
        ORDER BY createdAt ASC
        LIMIT 1
        """,
    )
    suspend fun getNextRunnableJob(): PostPublishJobEntity?

    @Query(
        """
        SELECT * FROM post_publish_jobs
        WHERE status IN ('queued', 'uploading', 'publishing', 'failed')
        """,
    )
    suspend fun getPendingJobs(): List<PostPublishJobEntity>

    @Query("UPDATE post_publish_jobs SET status = :status, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateJobStatus(jobId: String, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE post_publish_jobs
        SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt
        WHERE jobId = :jobId
        """,
    )
    suspend fun updateJobFailed(jobId: String, status: String, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE post_publish_jobs SET taskUuid = :taskUuid, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateTaskUuid(jobId: String, taskUuid: String?, updatedAt: Long)

    @Query("DELETE FROM post_publish_jobs WHERE jobId = :jobId")
    suspend fun deleteJob(jobId: String)

    @Query(
        """
        UPDATE post_publish_media
        SET uploadStatus = :status,
            uploadedUrl = :uploadedUrl,
            uploadedVideoUrl = :uploadedVideoUrl,
            errorMessage = :errorMessage
        WHERE mediaId = :mediaId
        """,
    )
    suspend fun updateMediaUploadResult(
        mediaId: String,
        status: String,
        uploadedUrl: String?,
        uploadedVideoUrl: String?,
        errorMessage: String?,
    )

    @Query("DELETE FROM post_publish_media WHERE mediaId = :mediaId")
    suspend fun deleteMedia(mediaId: String)

    @Query(
        """
        UPDATE post_publish_media
        SET uploadStatus = 'pending',
            uploadedUrl = NULL,
            uploadedVideoUrl = NULL,
            errorMessage = NULL
        WHERE jobId = :jobId AND uploadStatus != 'remote'
        """,
    )
    suspend fun resetLocalMediaUploads(jobId: String)

    @Query("UPDATE post_publish_media SET uploadStatus = :status WHERE mediaId = :mediaId")
    suspend fun updateMediaStatus(mediaId: String, status: String)
}
