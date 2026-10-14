package com.fox.music.core.assistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 助手动作意图：服务端 function calling 编排产生，端侧 dispatch 执行。
 * 字段与后端 routes/client/ai.js 的 actions[] 元素一一对应。
 */
@Serializable
data class AssistantAction(
    val type: String,
    val params: JsonObject = JsonObject(emptyMap()),
    val requiresConfirm: Boolean = false,
    val label: String = "",
) {
    /** 取字符串参数（不存在或类型不符返回 null） */
    fun paramString(key: String): String? =
        (params[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** 取整数参数（数字原语或纯数字字符串均可） */
    fun paramLong(key: String): Long? =
        (params[key] as? JsonPrimitive)?.content?.toLongOrNull()
}

/** POST /api/ai/chat 请求体 */
@Serializable
data class AiChatRequest(
    val conversationId: Long? = null,
    val message: String,
)

/** POST /api/ai/chat 的 data 部分 */
@Serializable
data class AiChatResult(
    val conversationId: Long,
    val reply: String,
    val actions: List<AssistantAction> = emptyList(),
    val usage: JsonObject? = null,
)

/** GET /api/ai/conversations 列表项 */
@Serializable
data class AiConversationSummary(
    val id: Long,
    val title: String? = null,
    val messageCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** 会话消息（GET /api/ai/conversations/:id；assistant 消息带 actions/usage） */
@Serializable
data class AiMessage(
    val id: Long,
    val role: String,
    val content: String? = null,
    val actions: List<AssistantAction>? = null,
    val usage: JsonObject? = null,
    val createdAt: String = "",
) {
    val isUser: Boolean get() = role == ROLE_USER
    val isAssistant: Boolean get() = role == ROLE_ASSISTANT

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/** GET /api/ai/conversations/:id 的 data 部分 */
@Serializable
data class AiConversationDetail(
    val id: Long,
    val title: String? = null,
    val messageCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val messages: List<AiMessage> = emptyList(),
)

/** GET /api/ai/conversations 的 data 部分（分页信封与后端 success() 一致） */
@Serializable
data class AiConversationPage(
    val list: List<AiConversationSummary> = emptyList(),
    val total: Int = 0,
    val current: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 0,
)
