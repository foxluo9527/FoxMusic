package com.fox.music.feature.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileState(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatar: String? = null,
    val signature: String? = null,
    val isFriend: Boolean = false,
    val isRequested: Boolean = false,
    val isSelf: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface UserProfileIntent : UiIntent {
    data object Load : UserProfileIntent
}

sealed interface UserProfileEffect : UiEffect {
    data class ShowMessage(val message: String) : UserProfileEffect
    data object NavigateBack : UserProfileEffect
    data class NavigateToChat(val userId: Long, val peerNickname: String?, val peerAvatar: String?) : UserProfileEffect
    data class NavigateToAddFriend(val userId: Long, val nickname: String?) : UserProfileEffect
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<UserProfileState, UserProfileIntent, UserProfileEffect>(
    UserProfileState(
        userId = savedStateHandle.get<Long>("userId") ?: 0L,
        nickname = savedStateHandle.get<String>("nickname")?.let(Uri::decode).orEmpty(),
        avatar = savedStateHandle.get<String>("avatar")?.let(Uri::decode)?.takeIf { it.isNotBlank() },
        signature = savedStateHandle.get<String>("signature")?.let(Uri::decode)?.takeIf { it.isNotBlank() },
        isFriend = savedStateHandle.get<Boolean>("isFriend") ?: false,
        isRequested = savedStateHandle.get<Boolean>("isRequested") ?: false,
    ),
) {

    override fun handleIntent(intent: UserProfileIntent) {
        when (intent) {
            UserProfileIntent.Load -> loadProfile()
        }
    }

    fun onBackClick() {
        sendEffect(UserProfileEffect.NavigateBack)
    }

    fun onSendMessageClick() {
        sendEffect(
            UserProfileEffect.NavigateToChat(
                userId = currentState.userId,
                peerNickname = currentState.nickname.takeIf { it.isNotBlank() },
                peerAvatar = currentState.avatar,
            ),
        )
    }

    fun onAddFriendClick() {
        sendEffect(
            UserProfileEffect.NavigateToAddFriend(
                userId = currentState.userId,
                nickname = currentState.nickname.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun onFriendRequestSent() {
        updateState { copy(isRequested = true) }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            val friendsDeferred = async { socialRepository.getFriends() }
            val profileDeferred = async { authRepository.getProfile() }

            val friendsResult = friendsDeferred.await()
            val profileResult = profileDeferred.await()

            var isFriend = currentState.isFriend
            var nickname = currentState.nickname
            var avatar = currentState.avatar
            var signature = currentState.signature
            var errorMessage: String? = null

            when (friendsResult) {
                is Result.Success -> {
                    val friend = friendsResult.data.firstOrNull { it.id == currentState.userId }
                    if (friend != null) {
                        isFriend = true
                        nickname = friend.mark?.takeIf { it.isNotBlank() }
                            ?: friend.nickname?.takeIf { it.isNotBlank() }
                            ?: friend.username?.takeIf { it.isNotBlank() }
                            ?: nickname
                        avatar = friend.avatar ?: avatar
                        signature = friend.signature ?: signature
                    } else {
                        isFriend = false
                    }
                }
                is Result.Error -> errorMessage = friendsResult.message
                is Result.Loading -> Unit
            }

            val isSelf = when (profileResult) {
                is Result.Success -> profileResult.data.id == currentState.userId
                is Result.Error -> {
                    if (errorMessage == null) errorMessage = profileResult.message
                    false
                }
                is Result.Loading -> false
            }

            updateState {
                copy(
                    nickname = nickname,
                    avatar = avatar,
                    signature = signature,
                    isFriend = isFriend,
                    isSelf = isSelf,
                    isLoading = false,
                    error = errorMessage,
                )
            }
        }
    }
}
