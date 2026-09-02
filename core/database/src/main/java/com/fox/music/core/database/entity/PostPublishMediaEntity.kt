package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "post_publish_media",
    foreignKeys = [
        ForeignKey(
            entity = PostPublishJobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class PostPublishMediaEntity(
    @PrimaryKey val mediaId: String,
    val jobId: String,
    val sortIndex: Int,
    /** image | video | live */
    val mediaType: String,
    val localUri: String? = null,
    val localVideoUri: String? = null,
    val remoteUrl: String? = null,
    val remoteVideoUrl: String? = null,
    /** pending | uploading | uploaded | remote | failed */
    val uploadStatus: String = "pending",
    val uploadedUrl: String? = null,
    val uploadedVideoUrl: String? = null,
    val errorMessage: String? = null,
)
