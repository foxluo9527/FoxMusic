package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fox.music.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE musicId = :musicId")
    suspend fun getByMusicId(musicId: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE musicId = :musicId")
    suspend fun updateStatus(musicId: Long, status: String)

    @Query(
        """
        UPDATE downloads SET
            progress = :progress,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes
        WHERE musicId = :musicId
        """,
    )
    suspend fun updateProgress(
        musicId: Long,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
    )

    @Query(
        """
        UPDATE downloads SET
            status = :status,
            filePath = :filePath,
            progress = :progress,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes
        WHERE musicId = :musicId
        """,
    )
    suspend fun updateDownloadResult(
        musicId: Long,
        status: String,
        filePath: String?,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
    )

    @Query("DELETE FROM downloads WHERE musicId = :musicId")
    suspend fun deleteByMusicId(musicId: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun countDownloading(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query(
        """
        SELECT filePath FROM downloads
        WHERE musicId = :musicId AND status = 'COMPLETED' AND filePath IS NOT NULL
        """,
    )
    suspend fun getCompletedFilePath(musicId: Long): String?

    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getDownloadingDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'PAUSED', 'FAILED')")
    suspend fun getResumableDownloads(): List<DownloadEntity>
}
