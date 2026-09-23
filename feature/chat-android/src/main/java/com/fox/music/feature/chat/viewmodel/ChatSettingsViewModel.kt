package com.fox.music.feature.chat.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.result.Result
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.model.chat.ChatConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatSettingsState(
    val userId: Long = 0L,
    val peerNickname: String? = null,
    val peerAvatar: String? = null,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val backgroundPath: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false,
)

@HiltViewModel
class ChatSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val preferencesDataStore: FoxPreferencesDataStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatSettingsState(
            userId = savedStateHandle.get<Long>("userId") ?: 0L,
            peerNickname = savedStateHandle.get<String>("peerNickname")
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.decode(it) },
            peerAvatar = savedStateHandle.get<String>("peerAvatar")
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.decode(it) },
        )
    )
    val state: StateFlow<ChatSettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        observeConversation()
    }

    private fun loadSettings() {
        val userId = _state.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            val isMuted = preferencesDataStore.isConversationMuted(userId)
            val backgroundPath = preferencesDataStore.getChatBackground(userId)
            _state.value = _state.value.copy(
                isMuted = isMuted,
                backgroundPath = backgroundPath,
            )
        }
    }

    private fun observeConversation() {
        val userId = _state.value.userId
        if (userId <= 0L) return

        chatRepository.observeConversations()
            .onEach { conversations ->
                val conversation = conversations.find { it.user.id == userId }
                if (conversation != null) {
                    _state.value = _state.value.copy(
                        isPinned = conversation.isPinned,
                        peerNickname = conversation.user.nickname ?: conversation.user.username,
                        peerAvatar = conversation.user.avatar,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun toggleMute() {
        val userId = _state.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            val newMuted = !_state.value.isMuted
            preferencesDataStore.setConversationMuted(userId, newMuted)
            _state.value = _state.value.copy(isMuted = newMuted)
        }
    }

    fun togglePin() {
        val userId = _state.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            val newPinned = !_state.value.isPinned
            when (val result = chatRepository.pinConversation(userId, newPinned)) {
                is Result.Success -> {
                    _state.value = _state.value.copy(isPinned = newPinned)
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(error = result.message ?: "操作失败")
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun setBackground(uri: Uri) {
        val userId = _state.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            try {
                val backgroundDir = File(context.filesDir, "chat_backgrounds")
                if (!backgroundDir.exists()) {
                    backgroundDir.mkdirs()
                }
                val targetFile = File(backgroundDir, "bg_$userId.jpg")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                preferencesDataStore.setChatBackground(userId, targetFile.absolutePath)
                _state.value = _state.value.copy(backgroundPath = targetFile.absolutePath)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "保存背景失败: ${e.message}")
            }
        }
    }

    fun clearBackground() {
        val userId = _state.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            val backgroundPath = _state.value.backgroundPath
            if (backgroundPath != null) {
                File(backgroundPath).delete()
            }
            preferencesDataStore.setChatBackground(userId, null)
            _state.value = _state.value.copy(backgroundPath = null)
        }
    }

    fun clearChatHistory() {
        val userId = _state.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            when (val result = chatRepository.clearChatHistory(userId)) {
                is Result.Success -> {
                    _state.value = _state.value.copy(isLoading = false, navigateBack = true)
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.message ?: "清空失败"
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
