package com.fox.music.feature.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class ChatDetailState(
    val userId: Long = 0L,
    val currentUserId: Long = 0L,
    val currentUserAvatar: String? = null,
    val peerAvatar: String? = null,
    val peerNickname: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val showEmojiPanel: Boolean = false,
    val showAttachmentSheet: Boolean = false,
    val isVoiceInputMode: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val recordingDurationSec: Int = 0,
    val error: String? = null,
) : UiState {
    val isSending: Boolean
        get() = messages.any { it.status == MessageStatus.SENDING }
}

sealed interface ChatDetailIntent : UiIntent {
    data object Load : ChatDetailIntent
    data class UpdateInput(val text: String) : ChatDetailIntent
    data object SendMessage : ChatDetailIntent
    data class SendEmoji(val emoji: String) : ChatDetailIntent
    data class SendImage(
        val uri: Uri,
        val sendOriginal: Boolean = false,
        val fileName: String? = null,
    ) : ChatDetailIntent
    data class SendVideo(val uri: Uri, val fileName: String?) : ChatDetailIntent
    data class SendFile(val uri: Uri, val fileName: String?) : ChatDetailIntent
    data class SendVoice(val uri: Uri, val durationMs: Long) : ChatDetailIntent
    data class RetryMessage(val localId: String) : ChatDetailIntent
    data object ToggleEmojiPanel : ChatDetailIntent
    data object ToggleAttachmentSheet : ChatDetailIntent
    data object ToggleVoiceInputMode : ChatDetailIntent
    data object DismissInputPanels : ChatDetailIntent
    data object StartVoiceRecord : ChatDetailIntent
    data object StopVoiceRecord : ChatDetailIntent
    data object CancelVoiceRecord : ChatDetailIntent
    data class UpdateRecordingDuration(val seconds: Int) : ChatDetailIntent
}

sealed interface ChatDetailEffect : UiEffect {
    data class ShowMessage(val message: String) : ChatDetailEffect
    data object NavigateBack : ChatDetailEffect
    data object RequestRecordPermission : ChatDetailEffect
}

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val getProfileUseCase: GetProfileUseCase,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<ChatDetailState, ChatDetailIntent, ChatDetailEffect>(
    ChatDetailState(userId = savedStateHandle.get<Long>("userId") ?: 0L),
) {

    init {
        val userId = currentState.userId
        viewModelScope.launch {
            val currentUserId = userPreferencesRepository.userPreferences.first().userId ?: 0L
            updateState { copy(currentUserId = currentUserId) }
        }
        viewModelScope.launch {
            when (val result = getProfileUseCase()) {
                is Result.Success -> updateState { copy(currentUserAvatar = result.data.avatar) }
                else -> Unit
            }
        }
        if (userId > 0L) {
            chatRepository.observeMessages(userId)
                .onEach { messages ->
                    updateState { copy(messages = messages, isLoading = false) }
                }
                .launchIn(viewModelScope)
            chatRepository.observeConversations()
                .onEach { conversations ->
                    val peer = conversations.find { it.user.id == userId }?.user
                    updateState {
                        copy(
                            peerAvatar = peer?.avatar,
                            peerNickname = peer?.nickname ?: peer?.username,
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    override fun handleIntent(intent: ChatDetailIntent) {
        when (intent) {
            ChatDetailIntent.Load -> loadChat()
            is ChatDetailIntent.UpdateInput -> updateState { copy(inputText = intent.text) }
            ChatDetailIntent.SendMessage -> sendText()
            is ChatDetailIntent.SendEmoji -> sendEmoji(intent.emoji)
            is ChatDetailIntent.SendImage -> sendMedia(
                uri = intent.uri,
                type = "file",
                fileName = intent.fileName,
                imageSendOriginal = intent.sendOriginal,
            )
            is ChatDetailIntent.SendVideo -> sendMedia(
                uri = intent.uri,
                type = "file",
                fileName = intent.fileName,
            )
            is ChatDetailIntent.SendFile -> sendMedia(
                uri = intent.uri,
                type = "file",
                fileName = intent.fileName,
            )
            is ChatDetailIntent.SendVoice -> sendMedia(
                uri = intent.uri,
                type = "voice",
                audioDurationMs = intent.durationMs,
                fileName = "voice_${System.currentTimeMillis()}.m4a",
            )
            is ChatDetailIntent.RetryMessage -> retry(intent.localId)
            ChatDetailIntent.ToggleEmojiPanel -> updateState {
                copy(
                    showEmojiPanel = !showEmojiPanel,
                    showAttachmentSheet = false,
                    isVoiceInputMode = false,
                )
            }
            ChatDetailIntent.ToggleAttachmentSheet -> updateState {
                copy(
                    showAttachmentSheet = !showAttachmentSheet,
                    showEmojiPanel = false,
                    isVoiceInputMode = false,
                )
            }
            ChatDetailIntent.ToggleVoiceInputMode -> updateState {
                copy(
                    isVoiceInputMode = !isVoiceInputMode,
                    showEmojiPanel = false,
                    showAttachmentSheet = false,
                )
            }
            ChatDetailIntent.DismissInputPanels -> updateState {
                if (!showEmojiPanel && !showAttachmentSheet) {
                    this
                } else {
                    copy(showEmojiPanel = false, showAttachmentSheet = false)
                }
            }
            ChatDetailIntent.StartVoiceRecord -> updateState {
                copy(
                    isRecordingVoice = true,
                    recordingDurationSec = 0,
                    showEmojiPanel = false,
                    showAttachmentSheet = false,
                )
            }
            ChatDetailIntent.StopVoiceRecord -> updateState { copy(isRecordingVoice = false) }
            ChatDetailIntent.CancelVoiceRecord -> updateState {
                copy(isRecordingVoice = false, recordingDurationSec = 0)
            }
            is ChatDetailIntent.UpdateRecordingDuration -> updateState {
                copy(recordingDurationSec = intent.seconds)
            }
        }
    }

    fun onBackClick() {
        sendEffect(ChatDetailEffect.NavigateBack)
    }

    private fun loadChat() {
        val userId = currentState.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            when (val sync = chatRepository.syncUnreadMessages(peerUserId = userId)) {
                is Result.Error -> updateState {
                    copy(isLoading = false, error = sync.message ?: "加载新消息失败")
                }
                else -> {
                    when (val read = chatRepository.markAsRead(userId)) {
                        is Result.Error -> updateState {
                            copy(isLoading = false, error = read.message ?: "标记已读失败")
                        }
                        else -> updateState { copy(isLoading = false, error = null) }
                    }
                }
            }
        }
    }

    private fun sendText() {
        val content = currentState.inputText.trim()
        if (content.isBlank()) return
        viewModelScope.launch {
            when (val result = chatRepository.sendTextMessage(currentState.userId, content)) {
                is Result.Success -> updateState { copy(inputText = "", showEmojiPanel = false) }
                is Result.Error -> sendEffect(ChatDetailEffect.ShowMessage(result.message ?: "发送失败"))
                is Result.Loading -> Unit
            }
        }
    }

    private fun sendEmoji(emoji: String) {
        if (currentState.inputText.isBlank()) {
            viewModelScope.launch {
                chatRepository.sendTextMessage(currentState.userId, emoji)
            }
        } else {
            updateState { copy(inputText = inputText + emoji) }
        }
    }

    private fun sendMedia(
        uri: Uri,
        type: String,
        fileName: String? = null,
        audioDurationMs: Long? = null,
        imageSendOriginal: Boolean = false,
    ) {
        viewModelScope.launch {
            updateState { copy(showAttachmentSheet = false) }
            when (
                val result = chatRepository.sendMediaMessage(
                    receiverId = currentState.userId,
                    type = type,
                    mediaUri = uri,
                    fileName = fileName,
                    audioDurationMs = audioDurationMs,
                    imageSendOriginal = imageSendOriginal,
                )
            ) {
                is Result.Success -> Unit
                is Result.Error -> sendEffect(ChatDetailEffect.ShowMessage(result.message ?: "发送失败"))
                is Result.Loading -> Unit
            }
        }
    }

    private fun retry(localId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.retryMessage(localId)) {
                is Result.Error -> sendEffect(ChatDetailEffect.ShowMessage(result.message ?: "重试失败"))
                else -> Unit
            }
        }
    }
}
