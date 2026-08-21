package com.fox.music.core.data.repository

import com.fox.music.core.data.importqueue.ImportLog
import com.fox.music.core.data.importqueue.PlaylistImportScheduler
import com.fox.music.core.database.dao.ImportJobDao
import com.fox.music.core.domain.repository.PlaylistImportProgress
import com.fox.music.core.domain.repository.PlaylistImportProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class PlaylistImportProgressRepositoryImpl @Inject constructor(
    private val importJobDao: ImportJobDao,
    private val scheduler: PlaylistImportScheduler,
) : PlaylistImportProgressRepository {

    override fun observe(playlistId: Long): Flow<PlaylistImportProgress?> =
        combine(
            importJobDao.observeLatestJobForPlaylist(playlistId),
            importJobDao.observeRunningTrackForPlaylist(playlistId),
        ) { job, running ->
            job?.takeIf { it.status != "completed" }?.let {
                PlaylistImportProgress(
                    jobId = it.jobId,
                    playlistId = it.playlistId,
                    status = it.status,
                    total = it.total,
                    done = it.done,
                    failed = it.failed,
                    currentTitle = running?.title,
                )
            }
        }

    override suspend fun pause(jobId: String) {
        scheduler.pause(jobId)
    }

    override suspend fun resume(jobId: String) {
        scheduler.resume(jobId)
    }

    override suspend fun retryFailed(jobId: String) {
        scheduler.retryFailed(jobId)
    }

    override suspend fun cancelFailed(jobId: String) {
        ImportLog.i("取消失败导入数据 jobId=$jobId")
        importJobDao.cancelFailed(jobId)
    }
}
