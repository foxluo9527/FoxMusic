package com.fox.music.feature.chat.ui.thread

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.chat.Message

/**
 * 聊天线程页（会话详情）的**跨端契约**。
 *
 * 与 Android 现有 `ChatDetailState` 的关系：
 *  - Android 的 ChatDetailViewModel 直接读本地 Room（`ChatRepository.observeMessages`），
 *    状态里混着 `android.net.Uri`、录音时长、聊天背景路径等 Android 专属字段；
 *  - iOS 没有本地库，用本契约 + Ktor REST（`api/messages/history/{userId}`、
 *    `POST api/messages`、`POST api/messages/read`）驱动**同一份 UI**。
 *
 * 两个契约表达的是同一页面语义，字段命名刻意保持一致；将来 Android 把媒体播放/相册选择
 * 抽成回调后即可收敛到本契约，届时删掉旧的那一份。
 */
data class ChatThreadState(
    val peerUserId: Long = 0L,
    val peerNickname: String? = null,
    val peerAvatar: String? = null,
    val currentUserId: Long = 0L,
    val currentUserAvatar: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val showEmojiPanel: Boolean = false,
    val showAttachmentPanel: Boolean = false,
    val isVoiceInputMode: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val recordingDurationSec: Int = 0,
    val isSelectionMode: Boolean = false,
    val selectedLocalIds: Set<String> = emptySet(),
    /** 正在播放的语音消息 localId（由宿主传入，播放器在平台侧） */
    val playingVoiceLocalId: String? = null,
    /** 聊天背景图的本地绝对路径（null = 用默认 surface 底色；由宿主平台提供） */
    val backgroundImagePath: String? = null,
    val error: String? = null,
) : UiState {
    val title: String
        get() = peerNickname?.takeIf { it.isNotBlank() } ?: "聊天"

    val selectedCount: Int
        get() = selectedLocalIds.size
}

sealed interface ChatThreadIntent : UiIntent {
    data object Load : ChatThreadIntent
    data class InputChange(val text: String) : ChatThreadIntent
    data object SendText : ChatThreadIntent
    data class SendEmoji(val emoji: String) : ChatThreadIntent
    data object ToggleEmojiPanel : ChatThreadIntent
    data object ToggleAttachmentPanel : ChatThreadIntent
    data class Retry(val localId: String) : ChatThreadIntent
    data class Recall(val messageId: Long, val localId: String) : ChatThreadIntent
    data class Delete(val localId: String) : ChatThreadIntent
    data class Copy(val content: String) : ChatThreadIntent
    data class EnterSelection(val localId: String) : ChatThreadIntent
    data class ToggleSelection(val localId: String) : ChatThreadIntent
    data object ExitSelection : ChatThreadIntent
    data object DeleteSelected : ChatThreadIntent
}

sealed interface ChatThreadEffect : UiEffect {
    data class ShowMessage(val message: String) : ChatThreadEffect
    data object NavigateBack : ChatThreadEffect
    data class NavigateToSettings(
        val userId: Long,
        val peerNickname: String?,
        val peerAvatar: String?,
    ) : ChatThreadEffect
    data class NavigateToUserProfile(val userId: Long) : ChatThreadEffect

    /** 转发：交由宿主进入「选择好友」流程 */
    data class Forward(val message: Message) : ChatThreadEffect
}
