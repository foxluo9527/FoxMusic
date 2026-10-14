package com.fox.music.feature.assistant.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantRepository
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.feature.assistant.AssistantChatMessage
import com.fox.music.feature.assistant.AssistantEffect
import com.fox.music.feature.assistant.AssistantIntent
import com.fox.music.feature.assistant.AssistantState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 助手状态机（Android 侧）。
 *
 * 数据流：输入 → [AssistantRepository.chat]（服务端 function calling 编排）→
 * 乐观消息落定 + assistant 回复上屏 → 动作意图交 [AssistantActionDispatcher]
 * 走端侧 UseCase/MusicController 执行（方案 4.1 铁律：服务端只给意图，不直执行）。
 *
 * 动作分发规则：
 *  - requiresConfirm（send_message/add_friend）进 pendingActions 队列，UI 确认后再跑；
 *  - 其余（play_music 等）收到即执行，结果 Toast 反馈。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository,
    private val assistantActionDispatcher: AssistantActionDispatcher,
) : MviViewModel<AssistantState, AssistantIntent, AssistantEffect>(AssistantState()) {

    /** 本地消息 id 序列（负数，与服务端 id 不撞） */
    private var localIdSeq = 0L

    private fun nextLocalId(): Long = --localIdSeq

    override fun handleIntent(intent: AssistantIntent) {
        when (intent) {
            is AssistantIntent.InputChanged -> updateState { copy(inputText = intent.text) }
            AssistantIntent.Send -> sendCurrent()
            is AssistantIntent.Retry -> retry(intent.messageId)
            is AssistantIntent.RunAction -> runAction(intent.action)
            is AssistantIntent.ConfirmAction -> confirmAction(intent.action)
            AssistantIntent.DismissAction ->
                updateState { copy(pendingActions = pendingActions.drop(1)) }
            AssistantIntent.NewConversation -> updateState { AssistantState() }
        }
    }

    private fun sendCurrent() {
        val text = currentState.inputText.trim()
        if (text.isEmpty() || currentState.isSending) return
        sendMessage(text)
    }

    private fun retry(messageId: Long) {
        val failed = currentState.messages.find { it.id == messageId } ?: return
        updateState { copy(messages = messages.filterNot { it.id == messageId }) }
        sendMessage(failed.content)
    }

    private fun sendMessage(text: String) {
        val localId = nextLocalId()
        updateState {
            copy(
                messages = messages + AssistantChatMessage(
                    id = localId,
                    isUser = true,
                    content = text,
                    isSending = true,
                ),
                inputText = "",
                isSending = true,
            )
        }
        viewModelScope.launch {
            try {
                val result = assistantRepository.chat(currentState.conversationId, text)
                updateState {
                    copy(
                        conversationId = result.conversationId,
                        messages = messages.map { msg ->
                            if (msg.id == localId) msg.copy(isSending = false) else msg
                        } + AssistantChatMessage(
                            id = nextLocalId(),
                            isUser = false,
                            content = result.reply,
                            actions = result.actions,
                        ),
                        isSending = false,
                    )
                }
                runActions(result.actions)
            } catch (e: Exception) {
                updateState {
                    copy(
                        messages = messages.map { msg ->
                            if (msg.id == localId) msg.copy(isSending = false, failed = true) else msg
                        },
                        isSending = false,
                    )
                }
                sendEffect(AssistantEffect.ShowMessage(e.message ?: "发送失败，请重试"))
            }
        }
    }

    private fun runAction(action: AssistantAction) {
        if (action.requiresConfirm) {
            updateState { copy(pendingActions = pendingActions + action) }
            return
        }
        viewModelScope.launch { dispatchAndToast(action) }
    }

    private fun runActions(actions: List<AssistantAction>) {
        actions.forEach { action ->
            if (action.requiresConfirm) {
                updateState { copy(pendingActions = pendingActions + action) }
            } else {
                viewModelScope.launch { dispatchAndToast(action) }
            }
        }
    }

    private fun confirmAction(action: AssistantAction) {
        updateState { copy(pendingActions = pendingActions - action) }
        viewModelScope.launch { dispatchAndToast(action) }
    }

    private suspend fun dispatchAndToast(action: AssistantAction) {
        val message = when (val result = assistantActionDispatcher.dispatch(action)) {
            is DispatchResult.Success -> result.message
            is DispatchResult.Failed -> result.reason
            is DispatchResult.Unsupported ->
                "「${action.label.ifBlank { action.type }}」暂不支持"
        }
        sendEffect(AssistantEffect.ShowMessage(message))
    }
}
