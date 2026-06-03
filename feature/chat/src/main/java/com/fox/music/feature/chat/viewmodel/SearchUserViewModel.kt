package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.chat.SearchedUser
import com.fox.music.feature.chat.util.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUserState(
    val query: String = "",
    val results: List<SearchedUser> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface SearchUserIntent : UiIntent {
    data class QueryChange(val query: String) : SearchUserIntent
    data object Search : SearchUserIntent
}

sealed interface SearchUserEffect : UiEffect {
    data class ShowMessage(val message: String) : SearchUserEffect
    data class NavigateToUserProfile(
        val userId: Long,
        val nickname: String?,
        val avatar: String?,
        val signature: String?,
        val isFriend: Boolean,
        val isRequested: Boolean,
    ) : SearchUserEffect
    data object NavigateBack : SearchUserEffect
}

@HiltViewModel
class SearchUserViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : MviViewModel<SearchUserState, SearchUserIntent, SearchUserEffect>(SearchUserState()) {

    override fun handleIntent(intent: SearchUserIntent) {
        when (intent) {
            is SearchUserIntent.QueryChange -> updateState { copy(query = intent.query, error = null) }
            SearchUserIntent.Search -> searchUsers()
        }
    }

    fun onBackClick() {
        sendEffect(SearchUserEffect.NavigateBack)
    }

    fun onUserClick(user: SearchedUser) {
        sendEffect(
            SearchUserEffect.NavigateToUserProfile(
                userId = user.id,
                nickname = user.displayName(),
                avatar = user.avatar,
                signature = user.signature,
                isFriend = user.isFriend,
                isRequested = user.isRequested,
            ),
        )
    }

    private fun searchUsers() {
        val keyword = currentState.query.trim()
        if (keyword.isBlank()) {
            sendEffect(SearchUserEffect.ShowMessage("请输入搜索关键词"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            when (val result = socialRepository.searchUsers(keyword)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            results = result.data,
                            isLoading = false,
                            hasSearched = true,
                            error = null,
                        )
                    }
                }
                is Result.Error -> {
                    updateState {
                        copy(
                            isLoading = false,
                            hasSearched = true,
                            error = result.message ?: "搜索失败",
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }
}
