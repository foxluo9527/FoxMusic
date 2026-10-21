package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fox.music.core.database.entity.ImportPlaylistJobEntity
import com.fox.music.core.database.entity.ImportTrackJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistJob(job: ImportPlaylistJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackJobs(tracks: List<ImportTrackJobEntity>)

    @Query("SELECT * FROM import_playlist_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getPlaylistJob(jobId: String): ImportPlaylistJobEntity?

    @Query("SELECT * FROM import_playlist_jobs WHERE playlistId = :playlistId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestJobForPlaylist(playlistId: Long): ImportPlaylistJobEntity?

    @Query("SELECT * FROM import_playlist_jobs WHERE playlistId = :playlistId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestJobForPlaylist(playlistId: Long): Flow<ImportPlaylistJobEntity?>

    @Query(
        """
        SELECT * FROM import_track_jobs
        WHERE status = 'queued'
          AND jobId NOT IN (SELECT jobId FROM import_playlist_jobs WHERE status = 'paused')
        ORDER BY createdAt ASC, trackIndex ASC
        LIMIT 1
        """,
    )
    suspend fun getNextQueuedTrack(): ImportTrackJobEntity?

    @Query(
        """
        UPDATE import_track_jobs
        SET status = 'queued', updatedAt = :updatedAt
        WHERE status = 'running' AND updatedAt < :staleBefore
        """,
    )
    suspend fun reclaimStaleRunningTracks(
        staleBefore: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("SELECT * FROM import_track_jobs WHERE jobId = :jobId ORDER BY trackIndex ASC")
    fun observeTracks(jobId: String): Flow<List<ImportTrackJobEntity>>

    /**
     * 导入详情页：按歌单取全部曲目（不再按 jobId 过滤，
     * 由调用方与最新 job 对齐；重试批次会重建 jobId 之外的记录不变）。
     */
    @Query("SELECT * FROM import_track_jobs WHERE playlistId = :playlistId ORDER BY trackIndex ASC")
    fun observeTracksForPlaylist(playlistId: Long): Flow<List<ImportTrackJobEntity>>

    /** 单曲重试：把该曲目重新放回待处理队列（错误信息一并清掉） */
    @Query(
        """
        UPDATE import_track_jobs
        SET status = 'queued', errorMessage = NULL, updatedAt = :updatedAt
        WHERE trackJobId = :trackJobId
        """,
    )
    suspend fun requeueTrack(trackJobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT * FROM import_track_jobs
        WHERE playlistId = :playlistId AND status = 'running'
        ORDER BY trackIndex ASC
        LIMIT 1
        """,
    )
    fun observeRunningTrackForPlaylist(playlistId: Long): Flow<ImportTrackJobEntity?>

    @Query("SELECT * FROM import_track_jobs WHERE trackJobId = :trackJobId LIMIT 1")
    suspend fun getTrack(trackJobId: String): ImportTrackJobEntity?

    /**
     * 回写原曲歌词（入队预取 / 详情页兜底拉取后缓存）。
     * 只在原来为空时写入，避免重复覆盖已有的完整歌词。
     */
    @Query(
        """
        UPDATE import_track_jobs SET lyrics = :lyrics, updatedAt = :updatedAt
        WHERE trackJobId = :trackJobId AND (lyrics IS NULL OR lyrics = '')
        """,
    )
    suspend fun fillTrackLyrics(
        trackJobId: String,
        lyrics: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query(
        """
        UPDATE import_track_jobs
        SET status = :status, errorMessage = :error, detailUrl = COALESCE(:detailUrl, detailUrl),
            matchScore = CASE WHEN :matchScore >= 0 THEN :matchScore ELSE matchScore END,
            titleRaw = COALESCE(:titleRaw, titleRaw),
            musicId = COALESCE(:musicId, musicId),
            rejectedLinksJson = COALESCE(:rejectedLinksJson, rejectedLinksJson),
            updatedAt = :updatedAt
        WHERE trackJobId = :trackJobId
        """,
    )
    suspend fun updateTrack(
        trackJobId: String,
        status: String,
        error: String? = null,
        detailUrl: String? = null,
        matchScore: Int = -1,
        titleRaw: String? = null,
        musicId: Long? = null,
        rejectedLinksJson: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        UPDATE import_playlist_jobs
        SET status = :status, done = :done, failed = :failed, updatedAt = :updatedAt
        WHERE jobId = :jobId
        """,
    )
    suspend fun updatePlaylistJob(
        jobId: String,
        status: String,
        done: Int,
        failed: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        UPDATE import_playlist_jobs SET status = 'paused', updatedAt = :updatedAt WHERE jobId = :jobId
        """,
    )
    suspend fun pausePlaylistJob(jobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE import_track_jobs SET status = 'paused', updatedAt = :updatedAt
        WHERE jobId = :jobId AND status IN ('queued', 'running')
        """,
    )
    suspend fun pauseTracksForJob(jobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE import_playlist_jobs SET status = 'queued', updatedAt = :updatedAt WHERE jobId = :jobId
        """,
    )
    suspend fun resumePlaylistJob(jobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE import_track_jobs SET status = 'queued', updatedAt = :updatedAt
        WHERE jobId = :jobId AND status = 'paused'
        """,
    )
    suspend fun resumeTracksForJob(jobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE import_track_jobs SET status = 'queued', errorMessage = NULL, updatedAt = :updatedAt
        WHERE jobId = :jobId AND status = 'failed'
        """,
    )
    suspend fun requeueFailedTracks(jobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM import_track_jobs WHERE jobId = :jobId AND status = 'completed'")
    suspend fun deleteCompletedTracks(jobId: String)

    @Query("DELETE FROM import_track_jobs WHERE jobId = :jobId AND status = 'failed'")
    suspend fun deleteFailedTracks(jobId: String)

    @Query("DELETE FROM import_track_jobs WHERE jobId = :jobId")
    suspend fun deleteAllTracks(jobId: String)

    @Query("DELETE FROM import_playlist_jobs WHERE jobId = :jobId")
    suspend fun deletePlaylistJob(jobId: String)

    @Query("SELECT COUNT(*) FROM import_track_jobs WHERE jobId = :jobId AND status = 'completed'")
    suspend fun countCompleted(jobId: String): Int

    @Query("SELECT COUNT(*) FROM import_track_jobs WHERE jobId = :jobId AND status = 'failed'")
    suspend fun countFailed(jobId: String): Int

    @Query("SELECT COUNT(*) FROM import_track_jobs WHERE jobId = :jobId AND status IN ('queued', 'running', 'paused')")
    suspend fun countRemaining(jobId: String): Int

    @Query("SELECT COUNT(*) FROM import_track_jobs WHERE jobId = :jobId")
    suspend fun countAllTracks(jobId: String): Int

    @Transaction
    suspend fun pauseJob(jobId: String) {
        pausePlaylistJob(jobId)
        pauseTracksForJob(jobId)
    }

    @Transaction
    suspend fun resumeJob(jobId: String) {
        resumePlaylistJob(jobId)
        resumeTracksForJob(jobId)
    }

    /**
     * 重试失败曲目：删除已成功记录，总数改为本批失败数，进度从 0 重新计。
     */
    @Transaction
    suspend fun retryFailed(jobId: String) {
        deleteCompletedTracks(jobId)
        val failedCount = countFailed(jobId)
        if (failedCount <= 0) {
            deleteJobCompletely(jobId)
            return
        }
        requeueFailedTracks(jobId)
        resetJobForRetryBatch(jobId, failedCount)
    }

    @Query(
        """
        UPDATE import_playlist_jobs
        SET total = :total, done = 0, failed = 0, status = 'queued', updatedAt = :updatedAt
        WHERE jobId = :jobId
        """,
    )
    suspend fun resetJobForRetryBatch(
        jobId: String,
        total: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * 导入全部成功：删除整批任务数据。
     */
    @Transaction
    suspend fun deleteJobCompletely(jobId: String) {
        deleteAllTracks(jobId)
        deletePlaylistJob(jobId)
    }

    /**
     * 批次结束仍有失败：丢掉已成功曲目记录，任务只保留失败项。
     */
    @Transaction
    suspend fun shrinkToFailedOnly(jobId: String) {
        deleteCompletedTracks(jobId)
        val failed = countFailed(jobId)
        if (failed <= 0) {
            deleteJobCompletely(jobId)
            return
        }
        updatePlaylistJobForNeedsRetry(jobId, total = failed, failed = failed)
    }

    @Query(
        """
        UPDATE import_playlist_jobs
        SET total = :total, done = 0, failed = :failed, status = 'needs_retry', updatedAt = :updatedAt
        WHERE jobId = :jobId
        """,
    )
    suspend fun updatePlaylistJobForNeedsRetry(
        jobId: String,
        total: Int,
        failed: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * 取消失败项：清除本导入任务全部本地队列数据（已入库歌曲不受影响）。
     */
    @Transaction
    suspend fun cancelFailed(jobId: String) {
        deleteJobCompletely(jobId)
    }

    @Transaction
    suspend fun claimNextRunnableTrack(): ImportTrackJobEntity? {
        reclaimStaleRunningTracks(System.currentTimeMillis() - STALE_RUNNING_MS)
        val track = getNextQueuedTrack() ?: return null
        updateTrack(track.trackJobId, status = "running")
        return track.copy(status = "running")
    }

    companion object {
        const val STALE_RUNNING_MS = 3 * 60 * 1000L
    }
}
