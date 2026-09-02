package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "post_publish_jobs")
data class PostPublishJobEntity(
    @PrimaryKey val jobId: String,
    val editingPostId: Long? = null,
    val title: String? = null,
    val content: String,
    val tagNames: List<String> = emptyList(),
    val tagIds: List<Long> = emptyList(),
    val musicId: Long? = null,
    /** queued | uploading | publishing | failed */
    val status: String = "queued",
    val errorMessage: String? = null,
    val taskUuid: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
