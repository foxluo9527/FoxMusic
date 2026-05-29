package com.fox.music.core.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fox.music.core.common.constants.AppConstants
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.data.mapper.toDownloadTask
import com.fox.music.core.database.dao.DownloadDao
import com.fox.music.core.database.entity.DownloadEntity
import com.fox.music.core.domain.repository.DownloadRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.model.music.Music
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) : DownloadRepository {

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(AppConstants.Network.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(AppConstants.Network.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(AppConstants.Network.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()

    override val downloads: Flow<List<DownloadTask>> =
        downloadDao.observeAll().map { entities -> entities.map { it.toDownloadTask() } }

    init {
        scope.launch {
            recoverStaleDownloadingTasks()
            startNextDownloads()
        }
    }

    override suspend fun enqueue(musics: List<Music>) {
        for (music in musics) {
            val existing = downloadDao.getByMusicId(music.id)
            when {
                existing?.status == DownloadStatus.COMPLETED.name -> continue
                existing == null -> {
                    downloadDao.insert(music.toDownloadEntity(DownloadStatus.PENDING))
                }
                existing.status == DownloadStatus.FAILED.name ||
                    existing.status == DownloadStatus.PAUSED.name ||
                    existing.status == DownloadStatus.DOWNLOADING.name -> {
                    if (!activeJobs.containsKey(music.id)) {
                        downloadDao.updateStatus(music.id, DownloadStatus.PENDING.name)
                    }
                    cancelledIds.remove(music.id)
                }
            }
        }
        startNextDownloads()
    }

    override suspend fun pause(musicId: Long) {
        cancelledIds.add(musicId)
        activeJobs[musicId]?.cancel()
        activeJobs.remove(musicId)
        downloadDao.updateStatus(musicId, DownloadStatus.PAUSED.name)
    }

    override suspend fun resume(musicId: Long) {
        cancelledIds.remove(musicId)
        downloadDao.updateStatus(musicId, DownloadStatus.PENDING.name)
        startNextDownloads()
    }

    override suspend fun cancel(musicId: Long) {
        cancelledIds.add(musicId)
        activeJobs[musicId]?.cancel()
        activeJobs.remove(musicId)
        val entity = downloadDao.getByMusicId(musicId) ?: return
        entity.filePath?.let { File(it).delete() }
        downloadDao.deleteByMusicId(musicId)
    }

    override suspend fun delete(musicId: Long) {
        cancel(musicId)
    }

    override suspend fun pauseAll() {
        activeJobs.keys.toList().forEach { pause(it) }
        val pending = downloadDao.getPendingDownloads()
        pending.forEach { downloadDao.updateStatus(it.musicId, DownloadStatus.PAUSED.name) }
    }

    override suspend fun resumeAll() {
        val resumable = downloadDao.getResumableDownloads()
        resumable.forEach {
            cancelledIds.remove(it.musicId)
            downloadDao.updateStatus(it.musicId, DownloadStatus.PENDING.name)
        }
        recoverStaleDownloadingTasks()
        startNextDownloads()
    }

    override suspend fun getTotalDownloadedBytes(): Long {
        var total = 0L
        val dir = downloadDir()
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) total += file.length()
            }
        }
        return total
    }

    private suspend fun recoverStaleDownloadingTasks() {
        downloadDao.getDownloadingDownloads().forEach { entity ->
            if (!activeJobs.containsKey(entity.musicId)) {
                downloadDao.updateStatus(entity.musicId, DownloadStatus.PENDING.name)
            }
        }
    }

    private suspend fun startNextDownloads() {
        queueMutex.withLock {
            recoverStaleDownloadingTasks()
            if (!canDownloadOnCurrentNetwork()) return

            // 仅以内存中的活跃任务数限制并发，避免 DB 残留 DOWNLOADING 占满槽位
            if (activeJobs.size >= MAX_CONCURRENT) return

            val slots = MAX_CONCURRENT - activeJobs.size
            downloadDao.getPendingDownloads()
                .take(slots)
                .forEach { entity ->
                    if (activeJobs.containsKey(entity.musicId)) return@forEach

                    val job = scope.launch {
                        try {
                            downloadFile(entity)
                        } finally {
                            activeJobs.remove(entity.musicId)
                            startNextDownloads()
                        }
                    }
                    activeJobs[entity.musicId] = job
                }
        }
    }

    private suspend fun downloadFile(entity: DownloadEntity) {
        if (cancelledIds.contains(entity.musicId)) return

        val resolvedUrl = MediaUrlResolver.resolve(entity.sourceUrl)
        if (resolvedUrl.isNullOrBlank()) {
            downloadDao.updateStatus(entity.musicId, DownloadStatus.FAILED.name)
            return
        }

        val downloadDir = downloadDir()
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val file = File(downloadDir, "${entity.musicId}.mp3")
        var downloadedBytes = if (file.exists()) file.length() else 0L

        downloadDao.update(
            entity.copy(
                status = DownloadStatus.DOWNLOADING.name,
                filePath = file.absolutePath,
                downloadedBytes = downloadedBytes,
                progress = 0,
            ),
        )

        try {
            val requestBuilder = Request.Builder().url(resolvedUrl)
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }
            val response = downloadClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("下载失败: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("响应体为空")
            val totalBytes = resolveTotalBytes(
                response = response,
                bodyContentLength = body.contentLength(),
                downloadedBytes = downloadedBytes,
                fallback = entity.totalBytes,
            )

            var lastProgressEmitMs = 0L
            var lastReportedProgress = -1
            var lastEmittedDownloadedBytes = downloadedBytes

            downloadDao.updateProgress(
                musicId = entity.musicId,
                progress = calculateProgress(downloadedBytes, totalBytes),
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
            )

            body.byteStream().use { input ->
                RandomAccessFile(file, "rw").use { output ->
                    output.seek(downloadedBytes)
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var chunksSinceEmit = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancelledIds.contains(entity.musicId)) {
                            downloadDao.updateStatus(entity.musicId, DownloadStatus.PAUSED.name)
                            return
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        chunksSinceEmit++

                        val progress = calculateProgress(downloadedBytes, totalBytes)
                        val now = System.currentTimeMillis()
                        val shouldEmit = when {
                            totalBytes > 0L -> {
                                progress != lastReportedProgress &&
                                    (
                                        now - lastProgressEmitMs >= PROGRESS_EMIT_INTERVAL_MS ||
                                            progress - lastReportedProgress >= 1 ||
                                            progress >= 99
                                        )
                            }
                            else -> {
                                downloadedBytes - lastEmittedDownloadedBytes >= UNKNOWN_TOTAL_EMIT_BYTES ||
                                    chunksSinceEmit >= 16 ||
                                    now - lastProgressEmitMs >= PROGRESS_EMIT_INTERVAL_MS
                            }
                        }
                        if (shouldEmit) {
                            downloadDao.updateProgress(
                                musicId = entity.musicId,
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                            lastProgressEmitMs = now
                            lastReportedProgress = progress
                            lastEmittedDownloadedBytes = downloadedBytes
                            chunksSinceEmit = 0
                            yield()
                        }
                    }
                }
            }

            downloadDao.update(
                entity.copy(
                    status = DownloadStatus.COMPLETED.name,
                    filePath = file.absolutePath,
                    progress = 100,
                    totalBytes = downloadedBytes,
                    downloadedBytes = downloadedBytes,
                ),
            )
        } catch (_: Exception) {
            if (cancelledIds.contains(entity.musicId)) {
                downloadDao.updateStatus(entity.musicId, DownloadStatus.PAUSED.name)
            } else {
                downloadDao.update(
                    entity.copy(
                        status = DownloadStatus.FAILED.name,
                        filePath = file.absolutePath,
                    ),
                )
            }
        }
    }

    private fun resolveTotalBytes(
        response: okhttp3.Response,
        bodyContentLength: Long,
        downloadedBytes: Long,
        fallback: Long,
    ): Long {
        val contentRangeTotal = response.header("Content-Range")
            ?.substringAfterLast('/')
            ?.toLongOrNull()
        return when {
            contentRangeTotal != null && contentRangeTotal > 0L -> contentRangeTotal
            response.code == 206 && bodyContentLength > 0L -> downloadedBytes + bodyContentLength
            bodyContentLength > 0L -> bodyContentLength
            fallback > 0L -> fallback
            else -> 0L
        }
    }

    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
    }

    private suspend fun canDownloadOnCurrentNetwork(): Boolean {
        val prefs = userPreferencesRepository.userPreferences.first()
        if (!prefs.downloadOnWifiOnly) return true
        return isWifiConnected()
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun downloadDir(): File = File(context.filesDir, "downloads")

    private fun Music.toDownloadEntity(status: DownloadStatus) = DownloadEntity(
        musicId = id,
        title = title,
        artistNames = artists.joinToString(", ") { it.name },
        coverUrl = coverImage,
        sourceUrl = url,
        status = status.name,
    )

    companion object {
        private const val MAX_CONCURRENT = 2
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L
        private const val UNKNOWN_TOTAL_EMIT_BYTES = 256 * 1024L
    }
}
