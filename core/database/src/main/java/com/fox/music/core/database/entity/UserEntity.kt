package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val username: String,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val isCurrentUser: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)
