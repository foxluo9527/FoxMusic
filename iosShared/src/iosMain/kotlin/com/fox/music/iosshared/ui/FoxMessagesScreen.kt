package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fox.music.core.data.mapper.toChatConversation
import com.fox.music.core.data.mapper.toNotification
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.NotificationDto
import com.fox.music.feature.chat.ui.screen.MessagesContent
import com.fox.music.feature.chat.util.NotificationFilter
import com.fox.music.feature.chat.viewmodel.MessagesState
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxChatRefresh
import com.fox.music.iosshared.FoxLocalChatStore
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 端消息页 Presenter：复用 feature:chat commonMain 的 MessagesContent ——
 * 与 Android 消息页同一份 UI（好友/评论/赞/系统四个入口 + 会话列表 + 删除对话框）。
 *
 * 数据全部 Kotlin 侧生产（Ktor REST），Swift 只接导航回调。
 *
 * 两个后端缺口（与 Android 的差异点，需知悉）：
 *  1. **会话删除 / 置顶**：Android 侧是纯本地 Room 操作（ChatRepositoryImpl 的
 *     deleteConversation / pinConversation 都不发请求），后端没有对应接口。
 *     iOS 没有本地库，这两个动作只能是**当前会话内的内存态** —— 一旦下拉刷新
 *     会从服务端重新拉列表而复原。要两端真正一致，需后端补
 *     `DELETE api/messages/conversations/{userId}` 与置顶接口。
 *  2. **未登录**：conversations / friends/requests / notifications 全是 auth 接口，
 *     未登录直接给空态（与 Android 未登录时列表为空的表现一致），不发无谓的 401 请求。
 */
object FoxMessagesScreen {

    private val api = FoxMusicApi()

    fun makeMessagesViewController(
        onConversationClick: (Long) -> Unit,
        onFriendsClick: () -> Unit,
        onCommentNotificationsClick: () -> Unit,
        onLikeNotificationsClick: () -> Unit,
        onSystemAnnouncementsClick: () -> Unit,
        onNotificationSettingsClick: () -> Unit,
        onSearchClick: () -> Unit,
        /** 顶部栏是否展示返回箭头（消息改为放抽屉的模态页时为 true）。 */
        showBackButton: Boolean = false,
        onBack: () -> Unit = {},
    ): UIViewController = foxComposeViewController {
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf(MessagesState(isLoading = true)) }

        suspend fun load(refreshing: Boolean) {
            if (!FoxAuthStore.isLoggedIn) {
                state = MessagesState(isLoading = false, isRefreshing = false)
                return
            }
            state = if (refreshing) {
                state.copy(isRefreshing = true, error = null)
            } else {
                state.copy(isLoading = true, error = null)
            }
            try {
                // 会话列表只走本地库（产品要求：不拉 api/messages/conversations）。
                // 会话由聊天页在发送/接收消息时写入 FoxLocalChatStore，删除/置顶也只改本地。
                val conversations = FoxLocalChatStore.loadConversations()
                    .sortedByDescending { conversationTimestamp(it) }
                // 次级数据失败不该把整页打成错误态：尽力而为，缺谁谁空。
                val friendRequests = runCatching { api.fetchFriendRequests() }.getOrNull()
                val notifications: List<NotificationDto> = runCatching {
                    api.fetchNotifications(page = 1, limit = 50)
                }.getOrDefault(PagedResponse()).list

                val mapped = notifications.map { it.toNotification() }
                val comments = NotificationFilter.commentNotifications(mapped)
                val likes = NotificationFilter.likeNotifications(mapped)
                val systems = NotificationFilter.systemNotifications(mapped)

                state = MessagesState(
                    friendRequestCount = friendRequests?.size ?: 0,
                    commentPreview = NotificationFilter.previewText(comments),
                    commentUnreadCount = NotificationFilter.unreadCount(comments),
                    likePreview = NotificationFilter.previewText(likes),
                    likeUnreadCount = NotificationFilter.unreadCount(likes),
                    systemPreview = NotificationFilter.previewText(systems),
                    systemUnreadCount = NotificationFilter.unreadCount(systems),
                    conversations = conversations,
                    isLoading = false,
                    isRefreshing = false,
                )
                // 持久化会话列表（断网时可展示本地缓存）
                if (conversations.isNotEmpty()) FoxLocalChatStore.saveConversations(conversations)
            } catch (e: Exception) {
                println("FoxMessagesScreen: 消息页加载失败: ${e.message}")
                // 加载失败回退本地缓存
                val cached = FoxLocalChatStore.loadConversations()
                state = MessagesState(
                    conversations = cached,
                    isLoading = false,
                    isRefreshing = false,
                    error = if (cached.isEmpty()) e.message else null,
                )
            }
        }

        // 登录/登出后 FoxAuthStore.revision 变化 → 重新拉一次（与首页同一套机制）；
        // 聊天页发/收消息后 FoxChatRefresh.conversationRevision 变化 → 也要重拉
        // （消息页的 Compose VC 常驻，从聊天页返回不会重建，不重拉就停在旧快照）；
        // 通知分类页标记已读后 notificationRevision 变化 → 红点立刻消失（不用手动下拉）。
        LaunchedEffect(
            FoxAuthStore.revision,
            FoxChatRefresh.conversationRevision,
            FoxChatRefresh.notificationRevision,
        ) { load(false) }

        MessagesContent(
            state = state,
            onRefresh = { scope.launch { load(true) } },
            onFriendsClick = onFriendsClick,
            onCommentNotificationsClick = onCommentNotificationsClick,
            onLikeNotificationsClick = onLikeNotificationsClick,
            onSystemAnnouncementsClick = onSystemAnnouncementsClick,
            onNotificationSettingsClick = onNotificationSettingsClick,
            onConversationClick = onConversationClick,
            // 见类注释：本地内存态，刷新会复原（后端无接口）
            onDeleteConversation = { userId ->
                state = state.copy(
                    conversations = state.conversations.filterNot { it.user.id == userId },
                )
                FoxLocalChatStore.deleteConversation(userId)
            },
            onPinConversation = { userId ->
                val updated = state.conversations.map {
                    if (it.user.id == userId) it.copy(isPinned = !it.isPinned) else it
                }
                state = state.copy(conversations = updated)
                FoxLocalChatStore.saveConversations(updated)
            },
            onSearchClick = onSearchClick,
            showBackButton = showBackButton,
            onBack = onBack,
        )
    }

    /**
     * 排序用的粗略时间戳：把 "yyyy-MM-dd HH:mm:ss" 压成数字串比较（与本地存储同一套写法）。
     *
     * 取不到时间（本地乐观消息还没拿到服务端时间）时返回 Long.MAX_VALUE —— 视作
     * 「最新」，否则刚发出去的会话会因为时间戳缺失被排到列表最底下。
     */
    private fun conversationTimestamp(conversation: ChatConversation): Long {
        val raw = conversation.updatedAt ?: conversation.lastMessage?.createdAt
        return raw?.filter { it.isDigit() }?.toLongOrNull() ?: Long.MAX_VALUE
    }
}
