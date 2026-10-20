package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.chat.Friend
import com.fox.music.feature.chat.util.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

// FriendsState / FriendsIntent / FriendsEffect 已下沉到
// :feature:chat commonMain 的 FriendsContract.kt（包名不变，两端共用同一份契约）。

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
