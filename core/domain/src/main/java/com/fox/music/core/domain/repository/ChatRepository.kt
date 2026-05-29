package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.PagedData
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun observeMessages(peerUserId: Long): Flow<List<Message>>

    fun observeConversations(): Flow<List<ChatConversation>>

    suspend fun syncConversations(): Result<Unit>

    suspend fun syncChatHistory(userId: Long, page: Int = 1, limit: Int = 50): Result<Unit>

    suspend fun sendTextMessage(receiverId: Long, content: String): Result<String>

    suspend fun sendMediaMessage(
        receiverId: Long,
        type: String,
        content: String = "",
        mediaUri: Uri? = null,
        fileName: String? = null,
        audioDurationMs: Long? = null,
        peerNickname: String? = null,
        peerAvatar: String? = null,
    ): Result<String>

    suspend fun retryMessage(localId: String): Result<Unit>

    suspend fun recallMessage(messageId: Long): Result<Unit>

    suspend fun markAsRead(targetId: Long): Result<Unit>

    suspend fun getUnreadMessages(): Result<List<Message>>

    @Deprecated("Use observeMessages + syncChatHistory", ReplaceWith("observeMessages(userId)"))
    suspend fun getConversations(): Result<List<ChatConversation>>

    @Deprecated("Use observeMessages + syncChatHistory", ReplaceWith("syncChatHistory(userId, page, limit)"))
    suspend fun getChatHistory(userId: Long, page: Int = 1, limit: Int = 20): Result<PagedData<Message>>

    @Deprecated("Use sendTextMessage", ReplaceWith("sendTextMessage(receiverId, content)"))
    suspend fun sendMessage(receiverId: Long, content: String, type: String = "text"): Result<Message>
}
