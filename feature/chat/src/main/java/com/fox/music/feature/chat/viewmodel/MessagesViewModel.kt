package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.feature.chat.util.NotificationFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    data class NavigateToChat(val userId: Long) : MessagesEffect
    data object NavigateBack : MessagesEffect
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val chatRepository: ChatRepository,
) : MviViewModel<MessagesState, MessagesIntent, MessagesEffect>(MessagesState()) {

    init {
        chatRepository.observeConversations()
            .onEach { conversations ->
                updateState { copy(conversations = conversations, isLoading = false) }
            }
            .launchIn(viewModelScope)

        EventViewModel.notificationsUpdated
            .onEach { loadMessages(refreshing = true) }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: MessagesIntent) {
        when (intent) {
            MessagesIntent.Load -> loadMessages(refreshing = false)
            MessagesIntent.Refresh -> loadMessages(refreshing = true)
        }
    }

    fun onFriendsClick() {
        sendEffect(MessagesEffect.NavigateToFriends)
    }

    fun onCommentNotificationsClick() {
        sendEffect(MessagesEffect.NavigateToCommentNotifications)
    }

    fun onLikeNotificationsClick() {
        sendEffect(MessagesEffect.NavigateToLikeNotifications)
    }

    fun onSystemAnnouncementsClick() {
        sendEffect(MessagesEffect.NavigateToSystemAnnouncements)
    }

    fun onConversationClick(userId: Long) {
        sendEffect(MessagesEffect.NavigateToChat(userId))
    }

    fun onBackClick() {
        sendEffect(MessagesEffect.NavigateBack)
    }

    fun deleteConversation(userId: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(userId)
        }
    }

    fun pinConversation(userId: Long) {
        viewModelScope.launch {
            val conversation = currentState.conversations.find { it.user.id == userId }
            if (conversation != null) {
                chatRepository.pinConversation(userId, !conversation.isPinned)
            }
        }
    }

    private fun loadMessages(refreshing: Boolean) {
        viewModelScope.launch {
            updateState {
                if (refreshing) {
                    copy(isRefreshing = true, error = null)
                } else {
                    copy(isLoading = true, error = null)
                }
            }

            val friendRequestsDeferred = async { socialRepository.getFriendRequests() }
            val notificationsDeferred = async { socialRepository.getNotifications(page = 1, limit = 50) }
            val conversationsDeferred = async { chatRepository.syncConversations() }
            val unreadMessagesDeferred = async { chatRepository.syncUnreadMessages(peerUserId = 0) }

            val friendRequestsResult = friendRequestsDeferred.await()
            val notificationsResult = notificationsDeferred.await()
            val conversationsResult = conversationsDeferred.await()
            val unreadMessagesResult = unreadMessagesDeferred.await()

            var errorMessage: String? = null

            val friendRequestCount = when (friendRequestsResult) {
                is Result.Success -> friendRequestsResult.data.size
                is Result.Error -> {
                    errorMessage = friendRequestsResult.message
                    0
                }
                is Result.Loading -> 0
            }

            val notifications = when (notificationsResult) {
                is Result.Success -> notificationsResult.data.list
                is Result.Error -> {
                    if (errorMessage == null) errorMessage = notificationsResult.message
                    emptyList()
                }
                is Result.Loading -> emptyList()
            }

            val commentNotifications = NotificationFilter.commentNotifications(notifications)
            val likeNotifications = NotificationFilter.likeNotifications(notifications)
            val systemNotifications = NotificationFilter.systemNotifications(notifications)

            if (conversationsResult is Result.Error && errorMessage == null) {
                errorMessage = conversationsResult.message
            }
            if (unreadMessagesResult is Result.Error && errorMessage == null) {
                errorMessage = unreadMessagesResult.message
            }

            updateState {
                copy(
                    friendRequestCount = friendRequestCount,
                    commentPreview = NotificationFilter.previewText(commentNotifications),
                    commentUnreadCount = NotificationFilter.unreadCount(commentNotifications),
                    likePreview = NotificationFilter.previewText(likeNotifications),
                    likeUnreadCount = NotificationFilter.unreadCount(likeNotifications),
                    systemPreview = NotificationFilter.previewText(systemNotifications),
                    systemUnreadCount = NotificationFilter.unreadCount(systemNotifications),
                    isLoading = false,
                    isRefreshing = false,
                    error = errorMessage,
                )
            }
        }
    }
}
