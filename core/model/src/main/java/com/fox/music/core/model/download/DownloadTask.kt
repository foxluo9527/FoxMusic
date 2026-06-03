package com.fox.music.core.model.download

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
}

data class DownloadTask(
    val musicId: Long,
    val title: String,
    val artistNames: String,
    val coverUrl: String?,
    val filePath: String?,
    val status: DownloadStatus,
    val progress: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val createdAt: Long,
    val lyrics: String? = null,
    val lyricsTrans: String? = null,
)
