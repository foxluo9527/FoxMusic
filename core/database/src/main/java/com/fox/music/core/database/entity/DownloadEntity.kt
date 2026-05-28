package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val musicId: Long,
    val title: String,
    val artistNames: String,
    val coverUrl: String? = null,
    val sourceUrl: String,
    val filePath: String? = null,
    val status: String,
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
