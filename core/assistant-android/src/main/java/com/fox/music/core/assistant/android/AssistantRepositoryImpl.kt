package com.fox.music.core.assistant.android

import com.fox.music.core.assistant.AssistantApiException
import com.fox.music.core.assistant.AssistantRepository
import com.fox.music.core.assistant.AiConversationDetail
import com.fox.music.core.assistant.AiConversationPage
import com.fox.music.core.assistant.AiChatRequest
import com.fox.music.core.assistant.AiChatResult
import com.fox.music.core.network.api.AiAssistantApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AssistantRepository 的 Retrofit 实现。
 * 后端错误（code != 200 或 data 为空）统一抛 AssistantApiException，
 * message 取后端返回原因，ViewModel 直接转 UiEffect。
 */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val api: AiAssistantApiService,
) : AssistantRepository {

    override suspend fun chat(conversationId: Long?, message: String): AiChatResult {
        val resp = api.chat(AiChatRequest(conversationId = conversationId, message = message))
        return resp.data ?: throw AssistantApiException(resp.message.ifBlank { "AI 助手无响应" })
    }

    override suspend fun listConversations(page: Int, limit: Int): AiConversationPage {
        val resp = api.listConversations(page = page, limit = limit)
        return resp.data ?: throw AssistantApiException(resp.message.ifBlank { "获取会话列表失败" })
    }

    override suspend fun getConversation(conversationId: Long): AiConversationDetail {
        val resp = api.getConversation(conversationId)
        return resp.data ?: throw AssistantApiException(resp.message.ifBlank { "获取会话详情失败" })
    }

    override suspend fun deleteConversation(conversationId: Long) {
        val resp = api.deleteConversation(conversationId)
        if (resp.data == null && !resp.isSuccess) {
            throw AssistantApiException(resp.message.ifBlank { "删除会话失败" })
        }
    }
}
