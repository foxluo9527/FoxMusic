package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerUserId: Long,
    val peerNickname: String? = null,
    val peerAvatar: String? = null,
    val peerMark: String? = null,
    val lastMessageLocalId: String? = null,
    val lastMessagePreview: String = "",
    val lastMessageStatus: String? = null,
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
