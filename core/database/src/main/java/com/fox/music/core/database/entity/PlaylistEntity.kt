package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String? = null,
    val coverImage: String? = null,
    val ownerId: Long? = null,
    val trackIdsJson: String? = null,
    val trackCount: Int = 0,
    val isPublic: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)
