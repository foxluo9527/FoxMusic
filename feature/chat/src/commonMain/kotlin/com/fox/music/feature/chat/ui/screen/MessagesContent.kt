package com.fox.music.feature.chat.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.feature.chat.ui.component.ConversationItem
import com.fox.music.feature.chat.ui.component.MessageCategoryItem
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.viewmodel.MessagesState

/**
 * 消息页内容（跨端共用）。
 *
 * 从 :feature:chat-android 的 MessagesScreen 抽出：原实现把「页面骨架」与
 * 「Android 专属的 ViewModel / Toast / effect 收集」揉在一起，iOS 拿不到。
 * 现在拆成两层 ——
 *
 *  - 本文件：纯 stateless，只吃 [MessagesState] + 回调，Android/iOS 同一份 UI
 *  - :feature:chat-android 的 MessagesScreen：薄壳，hiltViewModel + effect 收集 + Toast
 *  - iosShared 的 FoxMessagesScreen：薄壳，自己组装 state（Ktor REST）+ 同名回调
 *
 * `showBackButton` 区分两种入口：Tab 根页（false，自带"消息"大标题栏）与
 * 二级入口（true，TopAppBar 带返回箭头）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesContent(
    state: MessagesState,
    onRefresh: () -> Unit,
    onFriendsClick: () -> Unit,
    onCommentNotificationsClick: () -> Unit,
    onLikeNotificationsClick: () -> Unit,
    onSystemAnnouncementsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onConversationClick: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onPinConversation: (Long) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<ChatConversation?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        // Tab 根页（showBackButton=false）时，iOS 宿主 foxComposeViewController 已在外层
        // 加了 statusBarsPadding。若 Scaffold 再吃默认的 systemBars inset，标题会被
        // 叠加两次状态栏高度、比首页/广场整体下移一截（"对不齐"）。
        // 二级入口（showBackButton=true，带 TopAppBar）保留默认 inset 以避让底栏。
        contentWindowInsets = if (showBackButton) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets(0)
        },
        topBar = {
            if (showBackButton) {
                TopAppBar(
                    title = { Text("我的消息") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNotificationSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "通知设置")
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!showBackButton) {
                MessagesTabHeader(
                    onSearchClick = onSearchClick,
                )
            }
            MessagesList(
                state = state,
                onRefresh = onRefresh,
                onFriendsClick = onFriendsClick,
                onCommentNotificationsClick = onCommentNotificationsClick,
                onLikeNotificationsClick = onLikeNotificationsClick,
                onSystemAnnouncementsClick = onSystemAnnouncementsClick,
                onConversationClick = onConversationClick,
                onDeleteRequest = { conversation ->
                    selectedConversation = conversation
                    showDeleteDialog = true
                },
                onPinConversation = onPinConversation,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showDeleteDialog && selectedConversation != null) {
        val target = selectedConversation!!
        FoxDialog(
            title = "删除会话",
            text = "确定要删除与「${target.user.displayName()}」的会话吗？聊天记录将保留。",
            confirmText = "删除",
            onDismiss = {
                showDeleteDialog = false
                selectedConversation = null
            },
            onConfirm = {
                onDeleteConversation(target.user.id)
                showDeleteDialog = false
                selectedConversation = null
            },
            destructive = true,
        )
    }
}

@Composable
private fun MessagesTabHeader(
    onSearchClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = FoxSpacing.pagePadding,
                end = FoxSpacing.pagePadding - 4.dp,
                top = FoxSpacing.heroTop,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "消息",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        // 产品决策：Tab 根页顶部不放「通知设置」管理入口（只留搜索）。
        // 通知设置仍可从「我的消息」二级入口（showBackButton=true 的 TopAppBar）进入。
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesList(
    state: MessagesState,
    onRefresh: () -> Unit,
    onFriendsClick: () -> Unit,
    onCommentNotificationsClick: () -> Unit,
    onLikeNotificationsClick: () -> Unit,
    onSystemAnnouncementsClick: () -> Unit,
    onConversationClick: (Long) -> Unit,
    onDeleteRequest: (ChatConversation) -> Unit,
    onPinConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.conversations.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = FoxSpacing.tabChromeBottom),
        ) {
            item {
                MessageCategoryItem(
                    title = "我的好友",
                    subtitle = if (state.friendRequestCount > 0) {
                        "${state.friendRequestCount}条新申请"
                    } else {
                        "没有新通知"
                    },
                    icon = Icons.Default.Person,
                    iconBackgroundColor = Color(0xFFE53935),
                    unreadCount = state.friendRequestCount,
                    onClick = onFriendsClick,
                )
            }
            item {
                MessageCategoryItem(
                    title = "评论和@提及",
                    subtitle = state.commentPreview ?: "没有新通知",
                    icon = Icons.Default.ChatBubble,
                    iconBackgroundColor = Color(0xFF1E88E5),
                    unreadCount = state.commentUnreadCount,
                    onClick = onCommentNotificationsClick,
                )
            }
            item {
                MessageCategoryItem(
                    title = "赞和通知",
                    subtitle = state.likePreview ?: "没有新通知",
                    icon = Icons.Default.ThumbUp,
                    iconBackgroundColor = Color(0xFFFB8C00),
                    unreadCount = state.likeUnreadCount,
                    onClick = onLikeNotificationsClick,
                )
            }
            item {
                MessageCategoryItem(
                    title = "系统公告",
                    subtitle = state.systemPreview ?: "没有新公告",
                    icon = Icons.Default.Campaign,
                    iconBackgroundColor = Color(0xFF43A047),
                    unreadCount = state.systemUnreadCount,
                    onClick = onSystemAnnouncementsClick,
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            if (state.conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无私信会话",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = state.conversations.sortedByDescending { it.isPinned },
                    key = { it.id },
                ) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.user.id) },
                        onDeleteClick = { onDeleteRequest(conversation) },
                        onPinClick = { onPinConversation(conversation.user.id) },
                    )
                }
            }
        }
    }
}
