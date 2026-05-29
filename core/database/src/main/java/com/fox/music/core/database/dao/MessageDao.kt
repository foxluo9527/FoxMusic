package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fox.music.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY cachedAt ASC
        """,
    )
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE localId = :localId LIMIT 1")
    suspend fun getMessageByLocalId(localId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE serverId = :serverId LIMIT 1")
    suspend fun getMessageByServerId(serverId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: Long, limit: Int = 50): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isRead = 0")
    fun getUnreadCount(conversationId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query(
        """
        UPDATE messages SET
            status = :status,
            serverId = COALESCE(:serverId, serverId),
            content = COALESCE(:content, content),
            errorMessage = :errorMessage,
            localMediaUri = :localMediaUri
        WHERE localId = :localId
        """,
    )
    suspend fun updateMessageStatus(
        localId: String,
        status: String,
        serverId: Long? = null,
        content: String? = null,
        errorMessage: String? = null,
        localMediaUri: String? = null,
    )

    @Query("UPDATE messages SET isRead = 1 WHERE conversationId = :conversationId")
    suspend fun markConversationAsRead(conversationId: Long)

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun deleteMessageByLocalId(localId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
