package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.usecase.ResolveMusicCommentNavigationUseCase
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.resolveMusicCommentId
import com.fox.music.core.model.chat.resolvePostId
import com.fox.music.feature.chat.util.NotificationFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// NotificationCategory / NotificationCategoryState / Intent / Effect 已下沉到
// :feature:chat commonMain 的 NotificationCategoryContract.kt（包名不变，两端共用同一份契约）。

@HiltViewModel
class NotificationCategoryViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val resolveMusicCommentNavigationUseCase: ResolveMusicCommentNavigationUseCase,
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

    fun onNotificationClick(notification: Notification) {
        if (!notification.isRead) {
            sendIntent(NotificationCategoryIntent.MarkRead(notification.id))
        }
        if (currentState.category == NotificationCategory.SYSTEM) return

        notification.resolveMusicCommentId()?.let { commentId ->
            navigateToMusicComment(commentId)
            return
        }

        notification.resolvePostId()?.let { postId ->
            sendEffect(NotificationCategoryEffect.NavigateToPostDetail(postId))
        }
    }

    private fun navigateToMusicComment(commentId: Long) {
        viewModelScope.launch {
            when (val result = resolveMusicCommentNavigationUseCase(commentId)) {
                is Result.Success -> {
                    sendEffect(
                        NotificationCategoryEffect.NavigateToMusicComment(
                            musicId = result.data.musicId,
                            commentId = result.data.commentId,
                        )
                    )
                }
                is Result.Error -> {
                    sendEffect(
                        NotificationCategoryEffect.ShowMessage(
                            result.message ?: "无法打开音乐评论"
                        )
                    )
                }
                is Result.Loading -> Unit
            }
        }
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
                    val notifications = NotificationFilter.filterFor(
                        currentState.category,
                        result.data.list,
                    )
                    updateState {
                        copy(
                            notifications = notifications,
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
}
