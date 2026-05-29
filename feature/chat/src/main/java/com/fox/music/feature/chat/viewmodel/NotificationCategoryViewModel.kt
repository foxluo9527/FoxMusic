package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.chat.Notification
import com.fox.music.feature.chat.util.NotificationFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotificationCategory(val routeValue: String, val title: String) {
    COMMENT("comment", "评论和@提及"),
    LIKE("like", "赞和通知"),
    SYSTEM("system", "系统公告");

    companion object {
        fun fromRoute(value: String?): NotificationCategory? =
            entries.firstOrNull { it.routeValue == value }
    }
}

data class NotificationCategoryState(
    val category: NotificationCategory = NotificationCategory.COMMENT,
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface NotificationCategoryIntent : UiIntent {
    data object Load : NotificationCategoryIntent
    data object Refresh : NotificationCategoryIntent
    data class MarkRead(val notificationId: Long) : NotificationCategoryIntent
}

sealed interface NotificationCategoryEffect : UiEffect {
    data class ShowMessage(val message: String) : NotificationCategoryEffect
    data object NavigateBack : NotificationCategoryEffect
}

@HiltViewModel
class NotificationCategoryViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<NotificationCategoryState, NotificationCategoryIntent, NotificationCategoryEffect>(
    NotificationCategoryState(
        category = NotificationCategory.fromRoute(savedStateHandle.get<String>("type"))
            ?: NotificationCategory.COMMENT,
    ),
) {

    override fun handleIntent(intent: NotificationCategoryIntent) {
        when (intent) {
            NotificationCategoryIntent.Load,
            NotificationCategoryIntent.Refresh -> loadNotifications()
            is NotificationCategoryIntent.MarkRead -> markRead(intent.notificationId)
        }
    }

    fun onBackClick() {
        sendEffect(NotificationCategoryEffect.NavigateBack)
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            val type = when (currentState.category) {
                NotificationCategory.SYSTEM -> "system"
                else -> null
            }
            when (val result = socialRepository.getNotifications(page = 1, limit = 50, type = type)) {
                is Result.Success -> {
                    val filtered = filterByCategory(result.data.list, currentState.category)
                    updateState {
                        copy(
                            notifications = filtered,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                is Result.Error -> {
                    updateState {
                        copy(
                            isLoading = false,
                            error = result.message ?: "加载失败",
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun markRead(notificationId: Long) {
        viewModelScope.launch {
            when (val result = socialRepository.markNotificationRead(listOf(notificationId))) {
                is Result.Success -> {
                    updateState {
                        copy(
                            notifications = notifications.map { notification ->
                                if (notification.id == notificationId) {
                                    notification.copy(isRead = true)
                                } else {
                                    notification
                                }
                            },
                        )
                    }
                }
                is Result.Error -> {
                    sendEffect(NotificationCategoryEffect.ShowMessage(result.message ?: "标记已读失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun filterByCategory(
        notifications: List<Notification>,
        category: NotificationCategory,
    ): List<Notification> = when (category) {
        NotificationCategory.COMMENT -> NotificationFilter.commentNotifications(notifications)
        NotificationCategory.LIKE -> NotificationFilter.likeNotifications(notifications)
        NotificationCategory.SYSTEM -> NotificationFilter.systemNotifications(notifications)
    }
}
