package com.fox.music.feature.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.usecase.GetUserHomeProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

// UserProfileState / UserProfileIntent / UserProfileEffect 已下沉到
// :feature:chat commonMain 的 UserProfileContract.kt（包名不变，两端共用同一份契约）。

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository,
    private val getUserHomeProfileUseCase: GetUserHomeProfileUseCase,
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
            UserProfileIntent.ShowRemarkDialog -> updateState { copy(showRemarkDialog = true) }
            UserProfileIntent.DismissRemarkDialog -> updateState { copy(showRemarkDialog = false) }
            is UserProfileIntent.SubmitRemark -> submitRemark(intent.remark)
        }
    }

    private fun submitRemark(remark: String) {
        if (!currentState.isFriend || currentState.isSavingRemark) return
        val trimmed = remark.trim().take(MAX_REMARK_LENGTH)
        viewModelScope.launch {
            updateState { copy(isSavingRemark = true) }
            when (val result = socialRepository.setFriendRemark(currentState.userId, trimmed)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            mark = trimmed.takeIf { it.isNotBlank() },
                            isSavingRemark = false,
                            showRemarkDialog = false,
                        )
                    }
                    sendEffect(UserProfileEffect.ShowMessage("备注已更新"))
                }
                is Result.Error -> {
                    updateState { copy(isSavingRemark = false) }
                    sendEffect(UserProfileEffect.ShowMessage(result.message ?: "备注更新失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun onBackClick() {
        sendEffect(UserProfileEffect.NavigateBack)
    }

    fun onSendMessageClick() {
        sendEffect(
            UserProfileEffect.NavigateToChat(
                userId = currentState.userId,
                peerNickname = currentState.displayName.takeIf { it.isNotBlank() && it != "用户" },
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

    fun onUserHomeClick() {
        sendEffect(UserProfileEffect.NavigateToUserHome(currentState.userId))
    }

    fun onFriendRequestSent() {
        updateState { copy(isRequested = true) }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            val friendsDeferred = async { socialRepository.getFriends() }
            val profileDeferred = async { authRepository.getProfile() }
            val homeProfileDeferred = async { getUserHomeProfileUseCase(currentState.userId) }

            val friendsResult = friendsDeferred.await()
            val profileResult = profileDeferred.await()
            val homeProfileResult = homeProfileDeferred.await()

            var isFriend = currentState.isFriend
            var isRequested = currentState.isRequested
            var isPrivate = false
            var nickname = currentState.nickname
            var mark = currentState.mark
            var avatar = currentState.avatar
            var signature = currentState.signature
            var errorMessage: String? = null

            when (friendsResult) {
                is Result.Success -> {
                    val friend = friendsResult.data.firstOrNull { it.id == currentState.userId }
                    if (friend != null) {
                        isFriend = true
                        mark = friend.mark?.takeIf { it.isNotBlank() }
                        nickname = friend.nickname?.takeIf { it.isNotBlank() }
                            ?: friend.username?.takeIf { it.isNotBlank() }
                            ?: nickname
                        avatar = friend.avatar ?: avatar
                        signature = friend.signature ?: signature
                    } else {
                        isFriend = false
                        mark = null
                    }
                }
                is Result.Error -> errorMessage = friendsResult.message
                is Result.Loading -> Unit
            }

            when (homeProfileResult) {
                is Result.Success -> {
                    val home = homeProfileResult.data
                    isFriend = home.isFriend
                    isRequested = home.isRequested
                    isPrivate = home.isPrivate
                    nickname = home.nickname?.takeIf { it.isNotBlank() } ?: nickname
                    avatar = home.avatar ?: avatar
                    signature = home.signature ?: signature
                }
                is Result.Error -> {
                    if (errorMessage == null) errorMessage = homeProfileResult.message
                }
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
                    mark = mark,
                    avatar = avatar,
                    signature = signature,
                    isFriend = isFriend,
                    isRequested = isRequested,
                    isPrivate = isPrivate,
                    isSelf = isSelf,
                    isLoading = false,
                    error = errorMessage,
                )
            }
        }
    }

    companion object {
        /** 与共享契约 UserProfileState.MAX_REMARK_LENGTH 同源 */
        const val MAX_REMARK_LENGTH = UserProfileState.MAX_REMARK_LENGTH
    }
}
