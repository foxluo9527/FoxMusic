package com.fox.music.feature.assistant

import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState

/**
 * 对话面板的单条消息（UI 模型，**不是** wire DTO）。
 *
 * 用户消息先乐观上屏（[isSending] = true），服务端返回后再追加 assistant 消息；
 * 失败标 [failed] 支持原地重试。[id] 用本地负数自增序列，与服务端 id 不会混淆。
 */
data class AssistantChatMessage(
    val id: Long,
    val isUser: Boolean,
    val content: String,
    /** assistant 消息附带的动作意图（服务端 function calling 产物） */
    val actions: List<AssistantAction> = emptyList(),
    val isSending: Boolean = false,
    val failed: Boolean = false,
)

/**
 * AI 助手对话面板状态（跨端共用）。
 *
 * [conversationId] 由服务端首轮返回后回填，后续多轮记忆靠它续接；
 * 面板内有效（退出重进 = 新会话，历史列表留给后续增量）。
 */
data class AssistantState(
    val conversationId: Long? = null,
    val messages: List<AssistantChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    /** requiresConfirm 的动作队列（发消息/加好友等），逐条弹确认框 */
    val pendingActions: List<AssistantAction> = emptyList(),
) : UiState {
    val canSend: Boolean get() = inputText.isNotBlank() && !isSending
    val isEmpty: Boolean get() = messages.isEmpty()
}

sealed interface AssistantIntent : UiIntent {
    data class InputChanged(val text: String) : AssistantIntent

    /** 发送当前输入框内容 */
    data object Send : AssistantIntent

    /** 重试某条失败的用户消息 */
    data class Retry(val messageId: Long) : AssistantIntent

    /** 点动作芯片再次执行（非确认动作直接跑，确认动作入队） */
    data class RunAction(val action: AssistantAction) : AssistantIntent

    /** 用户确认执行待确认动作 */
    data class ConfirmAction(val action: AssistantAction) : AssistantIntent

    /** 取消队首待确认动作 */
    data object DismissAction : AssistantIntent

    /** 开新会话（清空本地消息与 conversationId） */
    data object NewConversation : AssistantIntent
}

sealed interface AssistantEffect : UiEffect {
    /** dispatch 结果 / 发送失败等原因，直接可 Toast */
    data class ShowMessage(val message: String) : AssistantEffect

    /**
     * 导航类动作：跳转到客户端某个页面（open_screen）。
     * core 层拿不到 NavController，故经 Effect 上浮给 Android 外壳接线。
     * [screen] 为服务端 SCREEN_WHITELIST 标识；[targetId] 为页面参数（歌单/专辑/歌手 ID，可空）。
     */
    data class OpenScreen(val screen: String, val targetId: Long? = null) : AssistantEffect

    /** 站内搜索（search_in_app）：带关键词跳转到搜索结果页 */
    data class SearchInApp(val query: String) : AssistantEffect
}
