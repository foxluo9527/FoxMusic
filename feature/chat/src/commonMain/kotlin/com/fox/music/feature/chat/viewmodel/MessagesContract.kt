package com.fox.music.feature.chat.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.chat.ChatConversation

/**
 * 消息页的 MVI 契约（跨端）。
 *
 * 从 :feature:chat-android 的 MessagesViewModel 抽出 —— iOS 端没有 Hilt/ViewModel，
 * 由 iosShared 的 FoxMessagesScreen 自己组装同一个 [MessagesState] 喂给共享的
 * MessagesContent，保证两端是**同一份 UI 契约**（字段含义不会两边跑偏）。
 */
data class MessagesState(
    val friendRequestCount: Int = 0,
    val commentPreview: String? = null,
    val commentUnreadCount: Int = 0,
    val likePreview: String? = null,
    val likeUnreadCount: Int = 0,
    val systemPreview: String? = null,
    val systemUnreadCount: Int = 0,
    val conversations: List<ChatConversation> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface MessagesIntent : UiIntent {
    data object Load : MessagesIntent
    data object Refresh : MessagesIntent
}

sealed interface MessagesEffect : UiEffect {
    data class ShowMessage(val message: String) : MessagesEffect
    data object NavigateToFriends : MessagesEffect
    data object NavigateToCommentNotifications : MessagesEffect
    data object NavigateToLikeNotifications : MessagesEffect
    data object NavigateToSystemAnnouncements : MessagesEffect
    data object NavigateToNotificationSettings : MessagesEffect
    data class NavigateToChat(val userId: Long) : MessagesEffect
    data object NavigateBack : MessagesEffect
}
