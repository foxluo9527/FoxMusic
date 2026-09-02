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
        SELECT * FROM (
            SELECT * FROM messages
            WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId
            ORDER BY cachedAt DESC
            LIMIT :limit
        )
        ORDER BY cachedAt ASC
        """,
    )
    fun observeMessages(ownerUserId: Long, conversationId: Long, limit: Int = 120): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE localId = :localId LIMIT 1")
    suspend fun getMessageByLocalId(localId: String): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE serverId = :serverId AND ownerUserId = :ownerUserId
        LIMIT 1
        """,
    )
    suspend fun getMessageByServerId(serverId: Long, ownerUserId: Long): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId
        ORDER BY cachedAt DESC LIMIT :limit
        """,
    )
    suspend fun getRecentMessages(ownerUserId: Long, conversationId: Long, limit: Int = 50): List<MessageEntity>

    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId AND isRead = 0
        """,
    )
    fun getUnreadCount(ownerUserId: Long, conversationId: Long): Flow<Int>

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
            localMediaUri = COALESCE(:localMediaUri, localMediaUri),
            remoteMediaUrl = COALESCE(:remoteMediaUrl, remoteMediaUrl),
            uploadedAt = COALESCE(:uploadedAt, uploadedAt),
            fileType = COALESCE(:fileType, fileType),
            localVideoUri = COALESCE(:localVideoUri, localVideoUri),
            remoteVideoUrl = COALESCE(:remoteVideoUrl, remoteVideoUrl)
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
        remoteMediaUrl: String? = null,
        uploadedAt: Long? = null,
        fileType: String? = null,
        localVideoUri: String? = null,
        remoteVideoUrl: String? = null,
    )

    @Query("UPDATE messages SET taskUuid = :taskUuid WHERE localId = :localId")
    suspend fun updateTaskUuid(localId: String, taskUuid: String)

    @Query(
        """
        UPDATE messages SET
            isRecalled = 1,
            content = :content,
            type = 'text',
            localMediaUri = NULL,
            remoteMediaUrl = NULL,
            localVideoUri = NULL,
            remoteVideoUrl = NULL,
            fileType = NULL
        WHERE localId = :localId
        """,
    )
    suspend fun markMessageRecalled(localId: String, content: String)

    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId
        AND isRead = 0 AND senderId != :currentUserId
        """,
    )
    suspend fun countIncomingUnread(ownerUserId: Long, conversationId: Long, currentUserId: Long): Int

    @Query(
        """
        SELECT * FROM messages
        WHERE ownerUserId = :ownerUserId
        AND status IN ('sending', 'failed') AND serverId IS NULL
        ORDER BY cachedAt ASC
        """,
    )
    suspend fun getPendingOutgoingMessages(ownerUserId: Long): List<MessageEntity>

    @Query(
        """
        UPDATE messages SET isRead = 1
        WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId
        """,
    )
    suspend fun markConversationAsRead(ownerUserId: Long, conversationId: Long)

    @Query(
        """
        SELECT * FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId
        ORDER BY cachedAt DESC LIMIT 1
        """,
    )
    suspend fun getLatestMessageByConversation(ownerUserId: Long, conversationId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun deleteMessageByLocalId(localId: String)

    @Query("DELETE FROM messages WHERE localId IN (:localIds)")
    suspend fun deleteMessagesByLocalIds(localIds: List<String>)

    @Query(
        """
        DELETE FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId
        """,
    )
    suspend fun deleteMessagesByConversation(ownerUserId: Long, conversationId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query(
        """
        SELECT * FROM messages
        WHERE ownerUserId = :ownerUserId
        AND content LIKE '%' || :query || '%'
        AND type = 'text'
        AND isRecalled = 0
        ORDER BY cachedAt DESC
        """,
    )
    fun searchMessages(ownerUserId: Long, query: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :peerUserId
        AND content LIKE '%' || :query || '%'
        AND type = 'text'
        AND isRecalled = 0
        ORDER BY cachedAt DESC
        """,
    )
    fun searchMessagesByUser(ownerUserId: Long, peerUserId: Long, query: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE ownerUserId = :ownerUserId AND conversationId = :peerUserId
        AND content LIKE '%' || :query || '%'
        AND type = 'text'
        AND isRecalled = 0
        """,
    )
    suspend fun countMessagesByUser(ownerUserId: Long, peerUserId: Long, query: String): Int
}
