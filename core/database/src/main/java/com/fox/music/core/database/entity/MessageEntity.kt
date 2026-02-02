package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val receiverId: Long,
    val content: String,
    val type: String = "text",
    val isRead: Boolean = false,
    val sentAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)
