package com.fox.music.feature.chat.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.chat.SearchedUser

/**
 * 「搜索用户（加好友）」页的 MVI 契约（跨端）。
 * 见 [FriendsState] 的说明 —— 与 Android 共用同一份字段语义。
 */
data class SearchUserState(
    val query: String = "",
    val results: List<SearchedUser> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface SearchUserIntent : UiIntent {
    data class QueryChange(val query: String) : SearchUserIntent
    data object Search : SearchUserIntent
}

sealed interface SearchUserEffect : UiEffect {
    data class ShowMessage(val message: String) : SearchUserEffect
    data class NavigateToUserProfile(
        val userId: Long,
        val nickname: String?,
        val avatar: String?,
        val signature: String?,
        val isFriend: Boolean,
        val isRequested: Boolean,
    ) : SearchUserEffect
    data object NavigateBack : SearchUserEffect
}
