package com.fox.music.core.data.mapper

import com.fox.music.core.database.entity.DownloadEntity
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import java.io.File

fun DownloadEntity.toDownloadTask(): DownloadTask = DownloadTask(
    musicId = musicId,
    title = title,
    artistNames = artistNames,
    coverUrl = coverUrl,
    filePath = filePath,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.PENDING),
    progress = progress,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    createdAt = createdAt,
)

fun DownloadTask.toMusic(): Music? {
    if (status != DownloadStatus.COMPLETED) return null
    val path = filePath?.takeIf { it.isNotBlank() } ?: return null
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return Music(
        id = musicId,
        title = title,
        url = file.toURI().toString(),
        coverImage = coverUrl,
        artists = artistNames.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, name ->
                Artist(id = musicId * 100 + index, name = name)
            },
    )
}
