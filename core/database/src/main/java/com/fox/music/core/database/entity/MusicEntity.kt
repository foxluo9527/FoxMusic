package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "musics")
data class MusicEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val url: String,
    val coverImage: String? = null,
    val duration: Long = 0,
    val genre: String? = null,
    val language: String? = null,
    val lyrics: String? = null,
    val playCount: Int = 0,
    val likeCount: Int = 0,
    val isFavorite: Boolean = false,
    val artistsJson: String? = null,
    val tagsJson: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)
