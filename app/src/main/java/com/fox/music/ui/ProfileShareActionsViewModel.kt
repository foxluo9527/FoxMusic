package com.fox.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileShareTarget(
    val userId: Long,
    val nickname: String?,
    val signature: String?,
)

data class ProfileShareActionsUiState(
    val target: ProfileShareTarget? = null,
    val showSelectFriend: Boolean = false,
)

sealed interface ProfileShareActionsEffect {
    data class ShowToast(val message: String) : ProfileShareActionsEffect
    data object NavigateToSelectFriend : ProfileShareActionsEffect
}

@HiltViewModel
class ProfileShareActionsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileShareActionsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ProfileShareActionsEffect>()
    val effect: SharedFlow<ProfileShareActionsEffect> = _effect.asSharedFlow()

    fun shareProfile(userId: Long, nickname: String?, signature: String?) {
        _uiState.update {
            it.copy(
                target = ProfileShareTarget(userId, nickname, signature),
                showSelectFriend = true,
            )
        }
        viewModelScope.launch {
            _effect.emit(ProfileShareActionsEffect.NavigateToSelectFriend)
        }
    }

    fun sendShareProfile(friendId: Long) {
        val target = _uiState.value.target ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showSelectFriend = false, target = null) }
            chatRepository.sendShareMessage(
                receiverId = friendId,
                shareType = "user",
                shareId = target.userId,
                content = "",
            ).onSuccess {
                _effect.emit(ProfileShareActionsEffect.ShowToast("分享成功"))
            }.onError { _, msg ->
                _effect.emit(ProfileShareActionsEffect.ShowToast(msg ?: "分享失败"))
            }
        }
    }

    fun dismissSelectFriend() {
        _uiState.update { it.copy(showSelectFriend = false, target = null) }
    }
}
