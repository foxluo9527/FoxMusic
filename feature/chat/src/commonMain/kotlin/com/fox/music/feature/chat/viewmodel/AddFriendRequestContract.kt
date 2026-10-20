package com.fox.music.feature.chat.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState

/**
 * 「发送好友申请」页的 MVI 契约（跨端）。
 *
 * 备注上限 20 字由 [MAX_MARK_LENGTH] 统一约束（两端输入框计数都必须一致）。
 */
const val MAX_MARK_LENGTH = 20

data class AddFriendRequestState(
    val userId: Long = 0L,
    val nickname: String = "",
    val message: String = "",
    val mark: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface AddFriendRequestIntent : UiIntent {
    data class MessageChange(val message: String) : AddFriendRequestIntent
    data class MarkChange(val mark: String) : AddFriendRequestIntent
    data object Submit : AddFriendRequestIntent
}

sealed interface AddFriendRequestEffect : UiEffect {
    data class ShowMessage(val message: String) : AddFriendRequestEffect
    data object NavigateBack : AddFriendRequestEffect
    data object RequestSent : AddFriendRequestEffect
}
