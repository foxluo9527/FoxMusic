package com.fox.music.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "conversations",
    primaryKeys = ["ownerUserId", "peerUserId"],
)
data class ConversationEntity(
    val ownerUserId: Long,
    val peerUserId: Long,
    val peerNickname: String? = null,
    val peerAvatar: String? = null,
    val peerMark: String? = null,
    val lastMessageLocalId: String? = null,
    val lastMessagePreview: String = "",
    val lastMessageStatus: String? = null,
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
)
