package com.fox.music.feature.assistant.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantActionTypes
import com.fox.music.core.assistant.AssistantRepository
import com.fox.music.core.assistant.AiConversationDetail
import com.fox.music.core.assistant.AiConversationPage
import com.fox.music.core.assistant.AiConversationSummary
import com.fox.music.core.assistant.AiMessage
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.core.assistant.autoExecute
import com.fox.music.core.assistant.needsConfirmDialog
import com.fox.music.core.common.cache.ListCacheKeys
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.feature.assistant.AssistantChatMessage
import com.fox.music.feature.assistant.AssistantEffect
import com.fox.music.feature.assistant.AssistantIntent
import com.fox.music.feature.assistant.AssistantState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/**
 * AI 助手状态机（Android 侧）。
 *
 * 数据流：输入 → [AssistantRepository.chat]（服务端 function calling 编排）→
 * 乐观消息落定 + assistant 回复上屏 → 动作意图交 [AssistantActionDispatcher]
 * 走端侧 UseCase/MusicController 执行（方案 4.1 铁律：服务端只给意图，不直执行）。
 *
 * 动作分发规则：
 *  - 需要弹窗确认的（send_message/add_friend）进 pendingActions 队列，UI 确认后再跑；
 *  - 播放类且服务端标了 requiresConfirm（用户只是询问/求推荐）→ 不执行，
 *    只作为消息上的可点击芯片，用户点击后才播；
 *  - 导航类（open_screen / search_in_app）不走 dispatcher，直接发 AssistantEffect 上浮给 UI 接线；
 *  - 其余（服务端判定用户明确要求立即执行的）收到即执行，结果 Toast 反馈。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository,
    private val assistantActionDispatcher: AssistantActionDispatcher,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<AssistantState, AssistantIntent, AssistantEffect>(AssistantState()) {

    /** 本地消息 id 序列（负数，与服务端 id 不撞） */
    private var localIdSeq = 0L
    private val json = Json { ignoreUnknownKeys = true }

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
            AssistantIntent.LoadConversations -> loadConversations()
            AssistantIntent.ToggleHistory -> updateState { copy(historyOpen = !historyOpen) }
            is AssistantIntent.OpenConversation -> openConversation(intent.conversationId)
            is AssistantIntent.DeleteConversation -> deleteConversation(intent.conversationId)
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
                persistCurrentConversation()
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
        // 点芯片 = 用户授权。播放类动作不弹窗，直接执行；
        // 发消息/加好友这类有外部副作用的动作才走确认对话框。
        if (action.needsConfirmDialog) {
            updateState { copy(pendingActions = pendingActions + action) }
            return
        }
        handleNonConfirmAction(action)
    }

    private fun loadConversations() {
        viewModelScope.launch {
            val owner = ownerId()
            val cached = readConversations(owner)
            if (cached != null) {
                updateState { copy(conversations = cached.list, historyOpen = true) }
            }
            try {
                val page = assistantRepository.listConversations()
                updateState { copy(conversations = page.list, historyOpen = true) }
                writeConversations(owner, page)
            } catch (e: Exception) {
                if (cached == null && currentState.conversations.isEmpty()) {
                    sendEffect(AssistantEffect.ShowMessage(e.message ?: "加载会话列表失败"))
                }
            }
        }
    }

    private fun openConversation(conversationId: Long) {
        viewModelScope.launch {
            val owner = ownerId()
            val cached = readConversation(owner, conversationId)
            if (cached != null) {
                showConversation(cached)
            }
            try {
                val detail = assistantRepository.getConversation(conversationId)
                showConversation(detail)
                writeConversation(owner, detail)
                writeConversations(
                    owner,
                    mergeSummary(readConversations(owner), detail),
                )
            } catch (e: Exception) {
                if (cached == null) {
                    sendEffect(AssistantEffect.ShowMessage(e.message ?: "加载会话失败"))
                }
            }
        }
    }

    private fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                assistantRepository.deleteConversation(conversationId)
                updateState { copy(conversations = conversations.filterNot { it.id == conversationId }) }
                val owner = ownerId()
                TabPageCache.invalidate(conversationKey(owner, conversationId))
                writeConversations(
                    owner,
                    AiConversationPage(list = currentState.conversations),
                )
                sendEffect(AssistantEffect.ShowMessage("已删除会话"))
            } catch (e: Exception) {
                sendEffect(AssistantEffect.ShowMessage(e.message ?: "删除会话失败"))
            }
        }
    }

    /**
     * 回复到达时的动作分流：
     *  - 弹窗类（发消息/加好友）→ 入 pendingActions 队列；
     *  - 播放类且需用户点击 → **什么都不做**，让它留在消息气泡上作为可点击芯片，等用户点；
     *  - 其余（服务端判定用户已明确要求立即执行）→ 直接执行。
     *
     * 之前「非确认动作一律收到即播」，用户只是问一句「周杰伦有什么歌」也会被突然播放，
     * 这个行为已被用户明确要求改掉。
     */
    private fun runActions(actions: List<AssistantAction>) {
        actions.forEach { action ->
            when {
                action.needsConfirmDialog ->
                    updateState { copy(pendingActions = pendingActions + action) }
                action.autoExecute -> handleNonConfirmAction(action)
                else -> Unit // 等用户点芯片
            }
        }
    }

    /**
     * 非确认动作分流：
     *  - open_screen / search_in_app 是导航类，core 层无 NavController，直接发 Effect 上浮给 UI；
     *  - 其余交 [assistantActionDispatcher] 执行（方案 4.1 铁律），结果 Toast 反馈。
     */
    private fun handleNonConfirmAction(action: AssistantAction) {
        when (action.type) {
            AssistantActionTypes.OPEN_SCREEN -> {
                val screen = action.paramString("screen")
                if (screen == null) {
                    sendEffect(AssistantEffect.ShowMessage("动作缺少 screen"))
                } else {
                    sendEffect(AssistantEffect.OpenScreen(screen, action.paramLong("target_id")))
                }
            }
            AssistantActionTypes.SEARCH_IN_APP -> {
                val keyword = action.paramString("keyword")
                if (keyword == null) {
                    sendEffect(AssistantEffect.ShowMessage("动作缺少 keyword"))
                } else {
                    sendEffect(AssistantEffect.SearchInApp(keyword))
                }
            }
            else -> viewModelScope.launch { dispatchAndToast(action) }
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

    private fun showConversation(detail: AiConversationDetail) {
        updateState {
            copy(
                conversationId = detail.id,
                messages = detail.messages.map { ai: AiMessage ->
                    AssistantChatMessage(
                        id = ai.id,
                        isUser = ai.isUser,
                        content = ai.content ?: "",
                        actions = ai.actions ?: emptyList(),
                    )
                },
                historyOpen = false,
            )
        }
    }

    private suspend fun persistCurrentConversation() {
        val id = currentState.conversationId ?: return
        val messages = currentState.messages.filter { !it.isSending && !it.failed }
        val owner = ownerId()
        val existing = readConversations(owner)?.list?.find { it.id == id }
        val detail = AiConversationDetail(
            id = id,
            title = existing?.title ?: messages.firstOrNull { it.isUser }?.content?.take(40),
            messageCount = messages.size,
            createdAt = existing?.createdAt ?: LocalDate.now().toString(),
            updatedAt = LocalDate.now().toString(),
            messages = messages.map { msg ->
                AiMessage(
                    id = msg.id,
                    role = if (msg.isUser) AiMessage.ROLE_USER else AiMessage.ROLE_ASSISTANT,
                    content = msg.content,
                    actions = msg.actions.takeIf { it.isNotEmpty() },
                )
            },
        )
        writeConversation(owner, detail)
        writeConversations(owner, mergeSummary(readConversations(owner), detail))
    }

    private fun mergeSummary(
        page: AiConversationPage?,
        detail: AiConversationDetail,
    ): AiConversationPage {
        val summary = AiConversationSummary(
            id = detail.id,
            title = detail.title,
            messageCount = detail.messageCount,
            createdAt = detail.createdAt,
            updatedAt = detail.updatedAt,
        )
        val rest = page?.list.orEmpty().filterNot { it.id == detail.id }
        return AiConversationPage(list = listOf(summary) + rest, total = rest.size + 1)
    }

    private suspend fun ownerId(): Long? =
        runCatching { userPreferencesRepository.userPreferences.first().userId }.getOrNull()

    private fun listKey(owner: Long?) = ListCacheKeys.key(owner, "ai", "conversations")

    private fun conversationKey(owner: Long?, id: Long) =
        ListCacheKeys.key(owner, "ai", "conversation", id.toString())

    private suspend fun readConversations(owner: Long?): AiConversationPage? {
        val raw = TabPageCache.readRawAsync(listKey(owner)) ?: return null
        return runCatching { json.decodeFromString(AiConversationPage.serializer(), raw) }.getOrNull()
    }

    private fun writeConversations(owner: Long?, page: AiConversationPage) {
        TabPageCache.putRaw(listKey(owner), json.encodeToString(AiConversationPage.serializer(), page))
    }

    private suspend fun readConversation(owner: Long?, id: Long): AiConversationDetail? {
        val raw = TabPageCache.readRawAsync(conversationKey(owner, id)) ?: return null
        return runCatching { json.decodeFromString(AiConversationDetail.serializer(), raw) }.getOrNull()
    }

    private fun writeConversation(owner: Long?, detail: AiConversationDetail) {
        TabPageCache.putRaw(
            conversationKey(owner, detail.id),
            json.encodeToString(AiConversationDetail.serializer(), detail),
        )
    }
}
