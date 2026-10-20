package com.fox.music.feature.chat.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest

/**
 * 「我的好友」页的 MVI 契约（跨端）。
 *
 * 从 :feature:chat-android 的 FriendsViewModel 抽出 —— 与消息页同一套做法：
 * iOS 端没有 Hilt/ViewModel，由 iosShared 的 FoxFriendsScreen 自己组装同一个
 * [FriendsState] 喂给共享的 FriendsContent，保证两端是**同一份 UI 契约**。
 */
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
