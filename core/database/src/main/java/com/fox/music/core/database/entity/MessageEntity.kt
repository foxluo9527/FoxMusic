package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "cachedAt"]),
        Index(value = ["serverId"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val localId: String,
    val serverId: Long? = null,
    val conversationId: Long,
    val senderId: Long,
    val receiverId: Long,
    val content: String,
    val type: String = "text",
    val status: String = "sent",
    val localMediaUri: String? = null,
    val localMediaFileName: String? = null,
    val remoteMediaUrl: String? = null,
    val fileType: String? = null,
    val uploadedAt: Long? = null,
    val taskUuid: String? = null,
    val imageSendOriginal: Boolean = false,
    val audioDurationMs: Long? = null,
    val errorMessage: String? = null,
    val isRecalled: Boolean = false,
    val isRead: Boolean = false,
    val sentAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
    val shareType: String? = null,
    val shareId: Long? = null,
    val shareDataJson: String? = null,
)
