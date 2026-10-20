package com.fox.music.core.data.importqueue

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fox.music.core.data.worker.PlaylistImportWorker
import com.fox.music.core.database.dao.ImportJobDao
import com.fox.music.core.database.entity.ImportPlaylistJobEntity
import com.fox.music.core.database.entity.ImportTrackJobEntity
import com.fox.music.core.domain.repository.ParsedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importJobDao: ImportJobDao,
) {

    suspend fun enqueuePlaylistImport(
        playlistId: Long,
        playlistTitle: String,
        tracks: List<ParsedTrack>,
        platform: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()
        ImportLog.i(
            "入队导入任务 jobId=$jobId playlistId=$playlistId " +
                "「$playlistTitle」曲目=${tracks.size} platform=$platform",
        )
        importJobDao.insertPlaylistJob(
            ImportPlaylistJobEntity(
                jobId = jobId,
                playlistId = playlistId,
                playlistTitle = playlistTitle,
                status = "queued",
                total = tracks.size,
                done = 0,
                failed = 0,
                platform = platform,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val trackEntities = tracks.mapIndexed { index, track ->
            ImportTrackJobEntity(
                trackJobId = UUID.randomUUID().toString(),
                jobId = jobId,
                playlistId = playlistId,
                trackIndex = index,
                title = track.title,
                artistNames = track.artists.joinToString(", "),
                durationMs = track.durationMs,
                coverUrl = track.cover,
                albumName = track.albumName,
                albumCover = track.albumCover,
                platform = platform,
                externalId = track.externalId,
                status = "queued",
                createdAt = now,
                updatedAt = now,
            )
        }
        importJobDao.insertTrackJobs(trackEntities)
        tracks.take(5).forEachIndexed { i, t ->
            ImportLog.i("入队曲目样例[#${i + 1}]: ${t.artists.joinToString(",")} - ${t.title}")
        }
        if (tracks.size > 5) ImportLog.i("入队曲目样例: …共 ${tracks.size} 首")
        kickWorker()
        return jobId
    }

    suspend fun pause(jobId: String) {
        ImportLog.i("暂停导入任务 jobId=$jobId")
        importJobDao.pauseJob(jobId)
        kickWorker()
    }

    suspend fun resume(jobId: String) {
        ImportLog.i("恢复导入任务 jobId=$jobId")
        importJobDao.resumeJob(jobId)
        kickWorker()
    }

    suspend fun retryFailed(jobId: String) {
        ImportLog.i("重试失败曲目 jobId=$jobId")
        importJobDao.retryFailed(jobId)
        kickWorker()
    }

    suspend fun cancelFailed(jobId: String) {
        ImportLog.i("取消失败曲目任务 jobId=$jobId")
        importJobDao.cancelFailed(jobId)
    }

    fun kickWorker(forceNew: Boolean = false) {
        ImportLog.i("调度 Worker forceNew=$forceNew")
        val request = OneTimeWorkRequestBuilder<PlaylistImportWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(PlaylistImportWorker.WORK_TAG)
            .build()
        val wm = WorkManager.getInstance(context)
        if (forceNew) {
            // Worker 内部续跑：不可 REPLACE unique work，否则会取消自身
            wm.enqueue(request)
        } else {
            wm.enqueueUniqueWork(
                PlaylistImportWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
