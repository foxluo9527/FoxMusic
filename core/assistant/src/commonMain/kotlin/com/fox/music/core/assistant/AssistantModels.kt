package com.fox.music.core.assistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
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

    /** 取整数数组参数（如 play_music_list 的 music_ids） */
    fun paramLongList(key: String): List<Long> =
        (params[key] as? JsonArray)?.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.toLongOrNull()
        } ?: emptyList()
}

/**
 * 播放类动作（play_music / play_playlist / play_album）。
 *
 * 播放是低成本、可逆的操作，端侧用「消息里的可点击芯片」承载即可，
 * 不需要弹确认对话框打断用户。
 */
val AssistantAction.isPlayback: Boolean
    get() = type.startsWith("play_")

/**
 * 是否必须弹确认对话框后执行。
 *
 * 交互约定（用户明确要求）：AI 给出的结果默认只是「可点击的候选」，
 * 要用户点了才真的播放；只有用户原话明确要求立即播放时，服务端才会把
 * 播放动作标成 requiresConfirm=false 由端侧收到即播。
 *
 * - 播放类动作：即使 requiresConfirm=true 也不弹窗，只渲染成芯片，点芯片即视为用户授权；
 * - 发消息 / 加好友等有外部副作用的动作：必须弹窗二次确认。
 */
val AssistantAction.needsConfirmDialog: Boolean
    get() = requiresConfirm && !isPlayback

/** 收到回复后是否可以立即自动执行（服务端判定用户已明确要求） */
val AssistantAction.autoExecute: Boolean
    get() = !requiresConfirm

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
