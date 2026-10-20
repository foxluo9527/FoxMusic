package com.fox.music.feature.chat.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.chat.Notification

/** 通知分类页的三个 tab（与路由参数 `type` 一一对应）。 */
enum class NotificationCategory(val routeValue: String, val title: String) {
    COMMENT("comment", "评论和@提及"),
    LIKE("like", "赞和通知"),
    SYSTEM("system", "系统公告");

    companion object {
        fun fromRoute(value: String?): NotificationCategory? =
            entries.firstOrNull { it.routeValue == value }
    }
}

/**
 * 通知分类页的 MVI 契约（跨端）。
 * iOS 端由 iosShared 的 FoxNotificationCategoryScreen 用 Ktor REST 组装同一个 [NotificationCategoryState]。
 */
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
    data class NavigateToPostDetail(val postId: Long) : NotificationCategoryEffect
    data class NavigateToMusicComment(val musicId: Long, val commentId: Long) : NotificationCategoryEffect
}
