package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 当前播放队列（按 queueIndex 有序）。
 * musicJson 为完整 [com.fox.music.core.model.music.Music] 序列化，含歌词，避免 DataStore 大 blob。
 */
@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey val queueIndex: Int,
    val musicId: Long,
    val musicJson: String,
)
