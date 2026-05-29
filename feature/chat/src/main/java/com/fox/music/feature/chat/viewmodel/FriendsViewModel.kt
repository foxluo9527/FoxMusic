package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.feature.chat.util.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsState(
    val friends: List<Friend> = emptyList(),
    val friendRequests: List<FriendRequest> = emptyList(),
    val isLoading: Boolean = false,
    val isAccepting: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface FriendsIntent : UiIntent {
    data object Load : FriendsIntent
    data object Refresh : FriendsIntent
    data class AcceptRequest(val requestId: Long) : FriendsIntent
}

sealed interface FriendsEffect : UiEffect {
    data class ShowMessage(val message: String) : FriendsEffect
    data object NavigateBack : FriendsEffect
    data object NavigateToSearch : FriendsEffect
    data class NavigateToUserProfile(
        val userId: Long,
        val nickname: String?,
        val avatar: String?,
        val signature: String?,
        val isFriend: Boolean,
        val isRequested: Boolean = false,
    ) : FriendsEffect
}

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : MviViewModel<FriendsState, FriendsIntent, FriendsEffect>(FriendsState()) {

    override fun handleIntent(intent: FriendsIntent) {
        when (intent) {
            FriendsIntent.Load,
            FriendsIntent.Refresh -> loadFriends()
            is FriendsIntent.AcceptRequest -> acceptRequest(intent.requestId)
        }
    }

    fun onBackClick() {
        sendEffect(FriendsEffect.NavigateBack)
    }

    fun onAddFriendClick() {
        sendEffect(FriendsEffect.NavigateToSearch)
    }

    fun onFriendClick(friend: Friend) {
        sendEffect(
            FriendsEffect.NavigateToUserProfile(
                userId = friend.id,
                nickname = friend.displayName(),
                avatar = friend.avatar,
                signature = friend.signature,
                isFriend = true,
            ),
        )
    }

    private fun loadFriends() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            val friendsDeferred = async { socialRepository.getFriends() }
            val requestsDeferred = async { socialRepository.getFriendRequests() }

            val friendsResult = friendsDeferred.await()
            val requestsResult = requestsDeferred.await()

            var errorMessage: String? = null

            val friends = when (friendsResult) {
                is Result.Success -> friendsResult.data
                is Result.Error -> {
                    errorMessage = friendsResult.message
                    emptyList()
                }
                is Result.Loading -> emptyList()
            }

            val requests = when (requestsResult) {
                is Result.Success -> requestsResult.data
                is Result.Error -> {
                    if (errorMessage == null) errorMessage = requestsResult.message
                    emptyList()
                }
                is Result.Loading -> emptyList()
            }

            updateState {
                copy(
                    friends = friends,
                    friendRequests = requests,
                    isLoading = false,
                    error = errorMessage,
                )
            }
        }
    }

    private fun acceptRequest(requestId: Long) {
        viewModelScope.launch {
            updateState { copy(isAccepting = true) }
            when (val result = socialRepository.acceptFriendRequest(requestId)) {
                is Result.Success -> {
                    sendEffect(FriendsEffect.ShowMessage("已添加好友"))
                    loadFriends()
                }
                is Result.Error -> {
                    sendEffect(FriendsEffect.ShowMessage(result.message ?: "接受申请失败"))
                }
                is Result.Loading -> Unit
            }
            updateState { copy(isAccepting = false) }
        }
    }
}
