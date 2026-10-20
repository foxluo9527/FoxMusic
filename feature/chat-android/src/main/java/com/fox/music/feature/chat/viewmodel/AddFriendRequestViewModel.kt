package com.fox.music.feature.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// AddFriendRequestState / AddFriendRequestIntent / AddFriendRequestEffect 已下沉到
// :feature:chat commonMain 的 AddFriendRequestContract.kt（包名不变，两端共用同一份契约）。

@HiltViewModel
class AddFriendRequestViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<AddFriendRequestState, AddFriendRequestIntent, AddFriendRequestEffect>(
    AddFriendRequestState(
        userId = savedStateHandle.get<Long>("userId") ?: 0L,
        nickname = savedStateHandle.get<String>("nickname")?.let(Uri::decode).orEmpty(),
    ),
) {

    override fun handleIntent(intent: AddFriendRequestIntent) {
        when (intent) {
            is AddFriendRequestIntent.MessageChange -> updateState { copy(message = intent.message) }
            is AddFriendRequestIntent.MarkChange -> {
                val mark = intent.mark.take(MAX_MARK_LENGTH)
                updateState { copy(mark = mark) }
            }
            AddFriendRequestIntent.Submit -> submitRequest()
        }
    }

    fun onBackClick() {
        sendEffect(AddFriendRequestEffect.NavigateBack)
    }

    private fun submitRequest() {
        val message = currentState.message.trim()
        if (message.isBlank()) {
            sendEffect(AddFriendRequestEffect.ShowMessage("请填写验证消息"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isSubmitting = true, error = null) }
            when (
                val result = socialRepository.sendFriendRequest(
                    userId = currentState.userId,
                    message = message,
                    mark = currentState.mark.trim().takeIf { it.isNotBlank() },
                )
            ) {
                is Result.Success -> {
                    sendEffect(AddFriendRequestEffect.ShowMessage("好友申请已发送"))
                    sendEffect(AddFriendRequestEffect.RequestSent)
                }
                is Result.Error -> {
                    sendEffect(AddFriendRequestEffect.ShowMessage(result.message ?: "发送失败"))
                }
                is Result.Loading -> Unit
            }
            updateState { copy(isSubmitting = false) }
        }
    }
}
