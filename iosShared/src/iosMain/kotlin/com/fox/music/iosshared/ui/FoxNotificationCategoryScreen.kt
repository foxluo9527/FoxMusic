package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fox.music.core.data.mapper.toNotification
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.resolveMusicCommentId
import com.fox.music.core.model.chat.resolvePostId
import com.fox.music.feature.chat.ui.screen.NotificationCategoryContent
import com.fox.music.feature.chat.util.NotificationFilter
import com.fox.music.feature.chat.viewmodel.NotificationCategory
import com.fox.music.feature.chat.viewmodel.NotificationCategoryState
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxChatRefresh
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 端通知分类页（评论和@ / 赞和通知 / 系统公告）：复用 NotificationCategoryContent。
 *
 * 数据源：`GET api/notifications`（SYSTEM 分类按 type=system 服务端过滤，其余客户端归类，
 * 与 Android NotificationCategoryViewModel 的取数口径一致）。
 * 点击通知 → `POST api/notifications/read` 标已读；帖子评论通知回调给 Swift 去跳帖子详情。
 * 音乐评论通知需要先把 commentId 解析成 musicId（Android 走
 * ResolveMusicCommentNavigationUseCase 打接口），iOS 侧暂以提示代替。
 */
object FoxNotificationCategoryScreen {

    private val api = FoxMusicApi()

    fun makeNotificationCategoryViewController(
        type: String,
        onBack: () -> Unit,
        onOpenPost: (postId: Long) -> Unit,
        onOpenMusicComment: (musicId: Long, commentId: Long, rootCommentId: Long) -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val scope = rememberCoroutineScope()
        val category = remember(type) {
            NotificationCategory.fromRoute(type) ?: NotificationCategory.COMMENT
        }
        var state by remember { mutableStateOf(NotificationCategoryState(category = category, isLoading = true)) }

        suspend fun load() {
            if (!FoxAuthStore.isLoggedIn) {
                state = state.copy(isLoading = false)
                return
            }
            state = state.copy(isLoading = state.notifications.isEmpty(), error = null)
            try {
                val serverType = if (category == NotificationCategory.SYSTEM) "system" else null
                val raw = api.fetchNotifications(page = 1, limit = 50, type = serverType)
                    .list
                    .map { it.toNotification() }
                state = state.copy(
                    notifications = NotificationFilter.filterFor(category, raw),
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                println("FoxNotificationCategoryScreen: 通知加载失败: ${e.message}")
                state = state.copy(isLoading = false, error = e.message ?: "加载失败")
            }
        }

        LaunchedEffect(category, FoxAuthStore.revision) { load() }

        NotificationCategoryContent(
            state = state,
            onBack = onBack,
            onNotificationClick = { notification ->
                if (!notification.isRead) {
                    scope.launch {
                        runCatching { api.markNotificationsRead(listOf(notification.id)) }
                        state = state.copy(
                            notifications = state.notifications.map {
                                if (it.id == notification.id) it.copy(isRead = true) else it
                            },
                        )
                        // 通知消息页常驻，不主动刷新红点会一直挂到下次下拉
                        FoxChatRefresh.bumpNotifications()
                    }
                }
                if (category == NotificationCategory.SYSTEM) return@NotificationCategoryContent
                scope.launch { openTarget(notification, onOpenPost, onOpenMusicComment, onToast) }
            },
        )
    }

    private suspend fun openTarget(
        notification: Notification,
        onOpenPost: (Long) -> Unit,
        onOpenMusicComment: (musicId: Long, commentId: Long, rootCommentId: Long) -> Unit,
        onToast: (String) -> Unit,
    ) {
        notification.resolvePostId()?.let {
            onOpenPost(it)
            return
        }
        val commentId = notification.resolveMusicCommentId()
        if (commentId != null) {
            // 与 Android ResolveMusicCommentNavigationUseCase 同口径：
            // replies 首条的 music_id 即所属歌曲；targetId 为根评论 ID。
            try {
                val musicId = api.fetchMusicCommentReplies(commentId, page = 1, limit = 20)
                    .list
                    .firstNotNullOfOrNull { it.musicId }
                if (musicId != null) {
                    onOpenMusicComment(musicId, commentId, commentId)
                } else {
                    onToast("无法定位音乐评论")
                }
            } catch (e: Exception) {
                onToast(e.message ?: "无法定位音乐评论")
            }
        }
    }
}
