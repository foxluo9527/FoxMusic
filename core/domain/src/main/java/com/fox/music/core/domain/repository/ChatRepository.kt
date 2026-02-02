package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.ChatConversation
import com.fox.music.core.model.Message
import com.fox.music.core.model.PagedData

interface ChatRepository {

    suspend fun getConversations(): Result<List<ChatConversation>>

    suspend fun getChatHistory(userId: Long, page: Int = 1, limit: Int = 20): Result<PagedData<Message>>

    suspend fun sendMessage(receiverId: Long, content: String, type: String = "text"): Result<Message>

    suspend fun recallMessage(messageId: Long): Result<Unit>

    suspend fun markAsRead(targetId: Long): Result<Unit>

    suspend fun getUnreadMessages(): Result<List<Message>>
}
