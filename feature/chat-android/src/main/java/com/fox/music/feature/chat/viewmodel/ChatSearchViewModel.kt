package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.model.chat.SearchResultItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatSearchState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
) : UiState

sealed interface ChatSearchIntent : UiIntent {
    data class UpdateQuery(val query: String) : ChatSearchIntent
    data object Search : ChatSearchIntent
}

sealed interface ChatSearchEffect : UiEffect {
    data class NavigateToUserSearch(
        val userId: Long,
        val nickname: String,
        val avatar: String?,
        val query: String,
    ) : ChatSearchEffect

    data class NavigateToChatDetail(val userId: Long, val messageId: Long?) : ChatSearchEffect
    data class NavigateToUserProfile(val userId: Long) : ChatSearchEffect
}

@HiltViewModel
class ChatSearchViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : MviViewModel<ChatSearchState, ChatSearchIntent, ChatSearchEffect>(ChatSearchState()) {

    private var searchJob: Job? = null

    override fun handleIntent(intent: ChatSearchIntent) {
        when (intent) {
            is ChatSearchIntent.UpdateQuery -> {
                updateState { copy(query = intent.query) }
                debounceSearch()
            }

            ChatSearchIntent.Search -> performSearch()
        }
    }

    fun onResultClick(item: SearchResultItem) {
        if (item.matchCount > 1) {
            sendEffect(
                ChatSearchEffect.NavigateToUserSearch(
                    userId = item.user.id,
                    nickname = item.user.nickname ?: "",
                    avatar = item.user.avatar,
                    query = currentState.query,
                )
            )
        } else {
            sendEffect(
                ChatSearchEffect.NavigateToChatDetail(
                    userId = item.user.id,
                    messageId = item.lastMessage.id,
                )
            )
        }
    }

    fun onUserProfileClick(userId: Long) {
        sendEffect(ChatSearchEffect.NavigateToUserProfile(userId))
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
            updateState { copy(results = emptyList(), isSearching = false) }
            return
        }

        chatRepository.searchMessages(query)
            .onEach { results ->
                updateState { copy(results = results, isSearching = false) }
            }
            .launchIn(viewModelScope)

        updateState { copy(isSearching = true) }
    }
}
