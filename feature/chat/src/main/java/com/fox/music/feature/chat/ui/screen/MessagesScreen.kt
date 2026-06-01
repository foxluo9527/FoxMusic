package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.feature.chat.ui.component.ConversationItem
import com.fox.music.feature.chat.ui.component.MessageCategoryItem
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.viewmodel.MessagesEffect
import com.fox.music.feature.chat.viewmodel.MessagesIntent
import com.fox.music.feature.chat.viewmodel.MessagesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToCommentNotifications: () -> Unit = {},
    onNavigateToLikeNotifications: () -> Unit = {},
    onNavigateToSystemAnnouncements: () -> Unit = {},
    onNavigateToChat: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<ChatConversation?>(null) }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(MessagesIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                MessagesEffect.NavigateBack -> onBack()
                MessagesEffect.NavigateToFriends -> onNavigateToFriends()
                MessagesEffect.NavigateToCommentNotifications -> onNavigateToCommentNotifications()
                MessagesEffect.NavigateToLikeNotifications -> onNavigateToLikeNotifications()
                MessagesEffect.NavigateToSystemAnnouncements -> onNavigateToSystemAnnouncements()
                is MessagesEffect.NavigateToChat -> onNavigateToChat(effect.userId)
                is MessagesEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("我的消息") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendIntent(MessagesIntent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                    onClick = viewModel::onFriendsClick,
                )
            }
            item {
                MessageCategoryItem(
                    title = "评论和@提及",
                    subtitle = state.commentPreview ?: "没有新通知",
                    icon = Icons.Default.ChatBubble,
                    iconBackgroundColor = Color(0xFF1E88E5),
                    unreadCount = state.commentUnreadCount,
                    onClick = viewModel::onCommentNotificationsClick,
                )
            }
            item {
                MessageCategoryItem(
                    title = "赞和通知",
                    subtitle = state.likePreview ?: "没有新通知",
                    icon = Icons.Default.ThumbUp,
                    iconBackgroundColor = Color(0xFFFB8C00),
                    unreadCount = state.likeUnreadCount,
                    onClick = viewModel::onLikeNotificationsClick,
                )
            }
            item {
                MessageCategoryItem(
                    title = "系统公告",
                    subtitle = state.systemPreview ?: "没有新公告",
                    icon = Icons.Default.Campaign,
                    iconBackgroundColor = Color(0xFF43A047),
                    unreadCount = state.systemUnreadCount,
                    onClick = viewModel::onSystemAnnouncementsClick,
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
                items(state.conversations.sortedByDescending { it.isPinned }, key = { it.id }) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { viewModel.onConversationClick(conversation.user.id) },
                        onDeleteClick = {
                            selectedConversation = conversation
                            showDeleteDialog = true
                        },
                        onPinClick = {
                            viewModel.pinConversation(conversation.user.id)
                        },
                    )
                }
            }
        }
        }
    }

    if (showDeleteDialog && selectedConversation != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除会话") },
            text = { Text("确定要删除与「${selectedConversation!!.user.displayName()}」的会话吗？聊天记录将保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(selectedConversation!!.user.id)
                        showDeleteDialog = false
                        selectedConversation = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
