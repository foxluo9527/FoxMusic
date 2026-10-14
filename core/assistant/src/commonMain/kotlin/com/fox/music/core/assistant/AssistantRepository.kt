package com.fox.music.core.assistant

/**
 * AI 助手数据入口（会话与对话）。
 *
 * 实现方（Android: core:assistant-android 的 AssistantRepositoryImpl 走 Retrofit；
 * iOS: iosShared 走 Ktor）负责把后端错误转换成抛出异常——
 * 调用方（ViewModel）统一 try/catch 转 UiEffect，失败信息直接透传。
 */
interface AssistantRepository {

    /** 发送一轮对话；conversationId 为空则服务端新建会话 */
    suspend fun chat(conversationId: Long?, message: String): AiChatResult

    /** 我的会话列表（服务端按 token 账号过滤） */
    suspend fun listConversations(page: Int = 1, limit: Int = 20): AiConversationPage

    /** 会话详情（含消息记录；非本人会话服务端返回 404，实现方抛异常） */
    suspend fun getConversation(conversationId: Long): AiConversationDetail

    /** 删除会话（软删） */
    suspend fun deleteConversation(conversationId: Long)
}

/** Repository 层统一异常（message 取后端返回的原因，直接可展示） */
class AssistantApiException(message: String) : Exception(message)
