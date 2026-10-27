package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fox.music.core.assistant.AiConversationDetail
import com.fox.music.core.assistant.AiConversationPage
import com.fox.music.core.assistant.AiConversationSummary
import com.fox.music.core.assistant.AiMessage
import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionTypes
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.core.assistant.needsConfirmDialog
import com.fox.music.feature.assistant.AssistantChatMessage
import com.fox.music.feature.assistant.AssistantState
import com.fox.music.feature.assistant.ui.AssistantContent
import com.fox.music.iosshared.FoxSceneCache
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.assistant.AssistantDispatcherIos
import com.fox.music.iosshared.assistant.AssistantPlayback
import com.fox.music.iosshared.assistant.FoxAssistantApi
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIViewController

/**
 * iOS 端 AI 助手面板（Increment 3）。
 *
 * 复用 feature:assistant 的同一份 [AssistantContent]（与 Android 共用 UI），
 * 本地用 remember 状态 + 协程作用域承载状态机（iOS 不走 Hilt/MviViewModel）。
 * 动作执行：导航类（open_screen / search_in_app）与点播类（play_music / play_playlist /
 * play_album）直接经 Swift 回调上浮——导航跳页、播放交给 Swift 的 AVPlayer（onPlaySongs）；
 * 其余经 [AssistantDispatcherIos] 在端侧执行，结果经 onMessage 透传 Swift Toast。
 */
object FoxAssistantScreen {

    private val api = FoxAssistantApi
    private val scope = foxMainScope()

    fun makeAssistantViewController(
        /** 跳转到客户端页面（open_screen）：screen 标识 + 可选 targetId */
        onOpenScreen: (String, Long?) -> Unit = { _, _ -> },
        /** 站内搜索（search_in_app） */
        onSearchInApp: (String) -> Unit = { _ -> },
        /** 点播（play_music / play_playlist / play_album）：折好的可播放队列 + 展示标题交给 Swift 播放 */
        onPlaySongs: (List<PlayableSong>, String) -> Unit = { _, _ -> },
        /** Kotlin 侧产生的提示（dispatch 结果 / 错误）→ Swift Toast */
        onMessage: (String) -> Unit = { _ -> },
        /** 返回（由 Swift 的导航栈处理） */
        onBack: () -> Unit = {},
    ): UIViewController = foxComposeViewController {
        var state by remember { mutableStateOf(AssistantState()) }
        var localSeq by remember { mutableStateOf(0L) }

        fun sendText(text: String) {
            val t = text.trim()
            if (t.isEmpty() || state.isSending) return
            val localId = --localSeq
            state = state.copy(
                messages = state.messages + AssistantChatMessage(
                    id = localId,
                    isUser = true,
                    content = t,
                    isSending = true,
                ),
                inputText = "",
                isSending = true,
            )
            scope.launch {
                try {
                    val result = api.chat(state.conversationId, t)
                    state = state.copy(
                        conversationId = result.conversationId,
                        messages = state.messages.map { msg ->
                            if (msg.id == localId) msg.copy(isSending = false) else msg
                        } + AssistantChatMessage(
                            id = --localSeq,
                            isUser = false,
                            content = result.reply,
                            actions = result.actions,
                        ),
                        isSending = false,
                    )
                    persistAssistantState(state)
                } catch (e: Exception) {
                    state = state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == localId) msg.copy(isSending = false, failed = true) else msg
                        },
                        isSending = false,
                    )
                    onMessage(e.message ?: "发送失败，请重试")
                }
            }
        }

        fun executeAction(action: AssistantAction) {
            when (action.type) {
                AssistantActionTypes.OPEN_SCREEN -> {
                    val screen = action.paramString("screen")
                    if (screen != null) onOpenScreen(screen, action.paramLong("target_id"))
                    else onMessage("动作缺少 screen")
                }
                AssistantActionTypes.SEARCH_IN_APP -> {
                    val keyword = action.paramString("keyword")
                    if (keyword != null) onSearchInApp(keyword) else onMessage("动作缺少 keyword")
                }
                AssistantActionTypes.PLAY_MUSIC,
                AssistantActionTypes.PLAY_PLAYLIST,
                AssistantActionTypes.PLAY_ALBUM,
                AssistantActionTypes.PLAY_MUSIC_LIST,
                -> scope.launch {
                    // iOS 播放器在 Swift：这里只取数据，折好队列后经 onPlaySongs 交给 Swift 入队播放。
                    when (val playback = AssistantDispatcherIos.loadPlayback(action)) {
                        is AssistantPlayback.Ready -> {
                            onPlaySongs(playback.songs, playback.title)
                            onMessage("正在播放${playback.title}")
                        }
                        is AssistantPlayback.Failed -> onMessage(playback.reason)
                    }
                }
                else -> scope.launch {
                    val result = AssistantDispatcherIos.dispatch(action)
                    val msg = when (result) {
                        is DispatchResult.Success -> result.message
                        is DispatchResult.Failed -> result.reason
                        is DispatchResult.Unsupported ->
                            "「${action.label.ifBlank { result.actionType }}」暂不支持"
                    }
                    onMessage(msg)
                }
            }
        }

        fun loadConversations() {
            scope.launch {
                val cached = loadCachedConversations()
                if (cached != null) {
                    state = state.copy(conversations = cached.list, historyOpen = true)
                }
                try {
                    val page = api.listConversations()
                    state = state.copy(conversations = page.list, historyOpen = true)
                    saveConversations(page)
                } catch (e: Exception) {
                    if (cached == null && state.conversations.isEmpty()) {
                        onMessage(e.message ?: "加载会话列表失败")
                    }
                }
            }
        }

        fun openConversation(id: Long) {
            scope.launch {
                val cached = loadCachedConversation(id)
                if (cached != null) state = state.withConversation(cached)
                try {
                    val detail = api.getConversation(id)
                    state = state.withConversation(detail)
                    saveConversation(detail)
                    saveConversations(mergeAssistantSummary(loadCachedConversations(), detail))
                } catch (e: Exception) {
                    if (cached == null) onMessage(e.message ?: "加载会话失败")
                }
            }
        }

        fun deleteConversation(id: Long) {
            scope.launch {
                try {
                    api.deleteConversation(id)
                    state = state.copy(conversations = state.conversations.filterNot { it.id == id })
                    FoxSceneCache.remove("ai", "conversation", id.toString())
                    saveConversations(AiConversationPage(list = state.conversations))
                    onMessage("已删除会话")
                } catch (e: Exception) {
                    onMessage(e.message ?: "删除会话失败")
                }
            }
        }

        AssistantContent(
            state = state,
            onInputChange = { state = state.copy(inputText = it) },
            onSend = { sendText(state.inputText) },
            onRetry = { id ->
                val failed = state.messages.find { it.id == id } ?: return@AssistantContent
                state = state.copy(messages = state.messages.filterNot { it.id == id })
                sendText(failed.content)
            },
            onRunAction = { action ->
                // 点芯片即用户授权：只有发消息/加好友这类有外部副作用的动作才弹确认框
                if (action.needsConfirmDialog) {
                    state = state.copy(pendingActions = state.pendingActions + action)
                } else {
                    executeAction(action)
                }
            },
            onConfirmAction = { action ->
                state = state.copy(pendingActions = state.pendingActions - action)
                executeAction(action)
            },
            onDismissAction = { state = state.copy(pendingActions = state.pendingActions.drop(1)) },
            onNewConversation = { state = AssistantState() },
            onLoadConversations = { loadConversations() },
            onOpenConversation = { openConversation(it) },
            onDeleteConversation = { deleteConversation(it) },
            onToggleHistory = { state = state.copy(historyOpen = !state.historyOpen) },
            onBack = onBack,
        )
    }

    /** 发送成功后把当前会话写回磁盘，并同步历史列表摘要。 */
    private suspend fun persistAssistantState(state: AssistantState) {
        val id = state.conversationId ?: return
        val messages = state.messages.filter { !it.isSending && !it.failed }
        val existing = loadCachedConversations()?.list?.find { it.id == id }
        val today = assistantToday()
        val detail = AiConversationDetail(
            id = id,
            title = existing?.title ?: messages.firstOrNull { it.isUser }?.content?.take(40),
            messageCount = messages.size,
            createdAt = existing?.createdAt ?: today,
            updatedAt = today,
            messages = messages.map { msg ->
                AiMessage(
                    id = msg.id,
                    role = if (msg.isUser) AiMessage.ROLE_USER else AiMessage.ROLE_ASSISTANT,
                    content = msg.content,
                    actions = msg.actions.takeIf { it.isNotEmpty() },
                )
            },
        )
        saveConversation(detail)
        saveConversations(mergeAssistantSummary(loadCachedConversations(), detail))
    }

    private suspend fun loadCachedConversations(): AiConversationPage? =
        FoxSceneCache.load(AiConversationPage.serializer(), "ai", "conversations")

    private suspend fun saveConversations(page: AiConversationPage) {
        FoxSceneCache.save(AiConversationPage.serializer(), page, "ai", "conversations")
    }

    private suspend fun loadCachedConversation(id: Long): AiConversationDetail? =
        FoxSceneCache.load(AiConversationDetail.serializer(), "ai", "conversation", id.toString())

    private suspend fun saveConversation(detail: AiConversationDetail) {
        FoxSceneCache.save(
            AiConversationDetail.serializer(),
            detail,
            "ai",
            "conversation",
            detail.id.toString(),
        )
    }

    private fun mergeAssistantSummary(
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

    private fun AssistantState.withConversation(detail: AiConversationDetail): AssistantState =
        copy(
            conversationId = detail.id,
            messages = detail.messages.map { ai ->
                AssistantChatMessage(
                    id = ai.id,
                    isUser = ai.isUser,
                    content = ai.content ?: "",
                    actions = ai.actions ?: emptyList(),
                )
            },
            historyOpen = false,
        )

    private fun assistantToday(): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.stringFromDate(NSDate())
    }
}
