package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toChatConversation
import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.model.ChatConversation
import com.fox.music.core.model.Message
import com.fox.music.core.model.PagedData
import com.fox.music.core.network.api.ChatApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApiService
) : ChatRepository {

    override suspend fun getConversations(): Result<List<ChatConversation>> = suspendRunCatching {
        val response = chatApi.getConversations()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toChatConversation() }
        } else throw Exception(response.message)
    }

    override suspend fun getChatHistory(
        userId: Long,
        page: Int,
        limit: Int
    ): Result<PagedData<Message>> = suspendRunCatching {
        val response = chatApi.getChatHistory(userId, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toMessage() }
        } else throw Exception(response.message)
    }

    override suspend fun sendMessage(
        receiverId: Long,
        content: String,
        type: String
    ): Result<Message> = suspendRunCatching {
        val response = chatApi.sendMessage(
            com.fox.music.core.network.model.SendMessageRequest(
                receiverId = receiverId,
                content = content,
                type = type
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toMessage()
        } else throw Exception(response.message)
    }

    override suspend fun recallMessage(messageId: Long): Result<Unit> = suspendRunCatching {
        val response = chatApi.recallMessage(messageId)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun markAsRead(targetId: Long): Result<Unit> = suspendRunCatching {
        val response = chatApi.markAsRead(
            com.fox.music.core.network.model.MarkChatReadRequest(targetId = targetId)
        )
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getUnreadMessages(): Result<List<Message>> = suspendRunCatching {
        val response = chatApi.getUnreadMessages()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toMessage() }
        } else throw Exception(response.message)
    }
}
