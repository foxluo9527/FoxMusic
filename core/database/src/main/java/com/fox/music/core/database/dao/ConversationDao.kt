package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fox.music.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT * FROM conversations
        WHERE ownerUserId = :ownerUserId
        ORDER BY isPinned DESC, lastMessageAt DESC
        """,
    )
    fun observeConversations(ownerUserId: Long): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        LIMIT 1
        """,
    )
    suspend fun getConversation(ownerUserId: Long, peerUserId: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query(
        """
        UPDATE conversations SET
            lastMessageLocalId = :localId,
            lastMessagePreview = :preview,
            lastMessageStatus = :status,
            lastMessageAt = :at,
            updatedAt = :at
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        """,
    )
    suspend fun updateLastMessage(
        ownerUserId: Long,
        peerUserId: Long,
        preview: String,
        status: String?,
        at: Long,
        localId: String?,
    )

    @Query(
        """
        UPDATE conversations SET unreadCount = 0
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        """,
    )
    suspend fun clearUnread(ownerUserId: Long, peerUserId: Long)

    @Query(
        """
        UPDATE conversations SET peerMark = :peerMark
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        """,
    )
    suspend fun updatePeerMark(ownerUserId: Long, peerUserId: Long, peerMark: String?)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query(
        """
        DELETE FROM conversations
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        """,
    )
    suspend fun deleteConversation(ownerUserId: Long, peerUserId: Long)

    @Query(
        """
        UPDATE conversations SET
            lastMessageLocalId = NULL,
            lastMessagePreview = '暂无新消息',
            lastMessageStatus = NULL,
            unreadCount = 0
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        """,
    )
    suspend fun clearConversationPreview(ownerUserId: Long, peerUserId: Long)

    @Query(
        """
        UPDATE conversations SET isPinned = :isPinned
        WHERE ownerUserId = :ownerUserId AND peerUserId = :peerUserId
        """,
    )
    suspend fun updatePinStatus(ownerUserId: Long, peerUserId: Long, isPinned: Boolean)

    @Query(
        """
        DELETE FROM conversations
        WHERE ownerUserId = :ownerUserId
        AND NOT EXISTS (
            SELECT 1 FROM messages m
            WHERE m.ownerUserId = conversations.ownerUserId
            AND m.conversationId = conversations.peerUserId
        )
        AND lastMessagePreview != '暂无新消息'
        """,
    )
    suspend fun deleteGhostConversations(ownerUserId: Long)
}
