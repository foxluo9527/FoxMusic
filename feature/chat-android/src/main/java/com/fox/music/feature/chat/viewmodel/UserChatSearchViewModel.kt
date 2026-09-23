package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.chat.Message
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserChatSearchState(
    val userId: Long = 0,
    val userNickname: String = "",
    val userAvatar: String? = null,
    val query: String = "",
    val messages: List<Message> = emptyList(),
    val matchCount: Int = 0,
    val isSearching: Boolean = false,
    val currentUserId: Long = 0L,
) : UiState

sealed interface UserChatSearchIntent : UiIntent {
    data class UpdateQuery(val query: String) : UserChatSearchIntent
    data object Search : UserChatSearchIntent
}

sealed interface UserChatSearchEffect : UiEffect {
    data class NavigateToChatDetail(val userId: Long, val messageId: Long?) : UserChatSearchEffect
    data class NavigateToUserProfile(val userId: Long) : UserChatSearchEffect
}

@HiltViewModel
class UserChatSearchViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<UserChatSearchState, UserChatSearchIntent, UserChatSearchEffect>(
    UserChatSearchState(
        userId = savedStateHandle.get<Long>("userId") ?: 0L,
        userNickname = savedStateHandle.get<String>("nickname") ?: "",
        userAvatar = savedStateHandle.get<String>("avatar"),
        query = savedStateHandle.get<String>("query") ?: "",
    )
) {

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val userId = userPreferencesRepository.userPreferences.first().userId ?: 0L
            updateState { copy(currentUserId = userId) }
            if (currentState.query.isNotBlank()) {
                performSearch()
            }
        }
    }

    override fun handleIntent(intent: UserChatSearchIntent) {
        when (intent) {
            is UserChatSearchIntent.UpdateQuery -> {
                updateState { copy(query = intent.query) }
                debounceSearch()
            }
            UserChatSearchIntent.Search -> performSearch()
        }
    }

    fun onMessageClick(message: Message) {
        sendEffect(
            UserChatSearchEffect.NavigateToChatDetail(
                userId = currentState.userId,
                messageId = message.id,
            )
        )
    }

    fun onUserProfileClick() {
        sendEffect(UserChatSearchEffect.NavigateToUserProfile(currentState.userId))
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch()
        }
    }

    private fun performSearch() {
        val query = currentState.query.trim()
        if (query.isBlank()) {
            updateState { copy(messages = emptyList(), matchCount = 0, isSearching = false) }
            return
        }

        val userId = currentState.userId

        chatRepository.searchMessagesByUser(userId, query)
            .onEach { messages ->
                updateState { copy(messages = messages, matchCount = messages.size, isSearching = false) }
            }
            .launchIn(viewModelScope)

        updateState { copy(isSearching = true) }
    }
}
