package com.fox.music.feature.chat.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState

/**
 * 「用户资料」页的 MVI 契约（跨端）。
 *
 * 注意两端的数据来源差异：
 *  - Android：`SocialRepository.getFriends()` + `AuthRepository.getProfile()` +
 *    `GetUserHomeProfileUseCase` 三路并发，friends 命中即可推定 isFriend 并带出备注。
 *  - iOS：没有本地好友库，直接以 `GET api/users/{userId}`（UserHomeProfileDto，后端已算好
 *    is_friend / is_requested / is_private / is_self）为准，好友备注从 `GET api/friends`
 *    里查（仅当 isFriend 为真时才多打一次请求）。
 * 两条路都收敛到同一个 [UserProfileState]，UI 因此完全一致。
 */
data class UserProfileState(
    val userId: Long = 0L,
    val nickname: String = "",
    val mark: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val isFriend: Boolean = false,
    val isRequested: Boolean = false,
    val isPrivate: Boolean = false,
    val isSelf: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showRemarkDialog: Boolean = false,
    val isSavingRemark: Boolean = false,
) : UiState {
    /** 展示名：有备注优先备注，否则昵称 */
    val displayName: String
        get() = mark?.takeIf { it.isNotBlank() } ?: nickname.takeIf { it.isNotBlank() } ?: "用户"

    companion object {
        /** 好友备注最大长度，两端输入框共用 */
        const val MAX_REMARK_LENGTH = 20
    }
}

sealed interface UserProfileIntent : UiIntent {
    data object Load : UserProfileIntent
    data object ShowRemarkDialog : UserProfileIntent
    data object DismissRemarkDialog : UserProfileIntent
    data class SubmitRemark(val remark: String) : UserProfileIntent
}

sealed interface UserProfileEffect : UiEffect {
    data class ShowMessage(val message: String) : UserProfileEffect
    data object NavigateBack : UserProfileEffect
    data class NavigateToChat(
        val userId: Long,
        val peerNickname: String?,
        val peerAvatar: String?,
    ) : UserProfileEffect
    data class NavigateToAddFriend(val userId: Long, val nickname: String?) : UserProfileEffect
    data class NavigateToUserHome(val userId: Long) : UserProfileEffect
}
