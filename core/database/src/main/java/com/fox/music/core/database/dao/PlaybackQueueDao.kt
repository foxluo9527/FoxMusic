package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fox.music.core.database.entity.PlaybackQueueEntity

@Dao
interface PlaybackQueueDao {

    @Query("SELECT * FROM playback_queue ORDER BY queueIndex ASC")
    suspend fun getAllOrdered(): List<PlaybackQueueEntity>

    @Query("SELECT COUNT(*) FROM playback_queue")
    suspend fun count(): Int

    @Query("DELETE FROM playback_queue")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaybackQueueEntity>)

    @Transaction
    suspend fun replaceAll(items: List<PlaybackQueueEntity>) {
        clear()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}
