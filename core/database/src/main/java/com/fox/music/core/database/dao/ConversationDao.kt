package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fox.music.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE peerUserId = :peerUserId LIMIT 1")
    suspend fun getConversation(peerUserId: Long): ConversationEntity?

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
        WHERE peerUserId = :peerUserId
        """,
    )
    suspend fun updateLastMessage(
        peerUserId: Long,
        preview: String,
        status: String?,
        at: Long,
        localId: String?,
    )

    @Query("UPDATE conversations SET unreadCount = 0 WHERE peerUserId = :peerUserId")
    suspend fun clearUnread(peerUserId: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
