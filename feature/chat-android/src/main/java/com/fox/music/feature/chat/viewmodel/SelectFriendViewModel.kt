package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.chat.Friend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectFriendState(
    val friends: List<Friend> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SelectFriendViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectFriendState())
    val state: StateFlow<SelectFriendState> = _state.asStateFlow()

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = socialRepository.getFriends()) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        friends = result.data,
                        isLoading = false,
                    )
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.message ?: "加载好友列表失败",
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }
}
