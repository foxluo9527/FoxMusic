package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fox.music.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: Long, limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isRead = 0")
    fun getUnreadMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isRead = 0")
    fun getUnreadCount(conversationId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isRead = 1 WHERE conversationId = :conversationId")
    suspend fun markConversationAsRead(conversationId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
