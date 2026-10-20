package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.MessagesEffect
import com.fox.music.feature.chat.viewmodel.MessagesIntent
import com.fox.music.feature.chat.viewmodel.MessagesViewModel

/**
 * 消息页（Android 薄壳）。
 *
 * UI 本身（页面骨架 + 四类入口 + 会话列表 + 删除对话框）在与
 * :feature:chat commonMain 的 MessagesContent —— iOS 端 iosShared 的
 * FoxMessagesScreen 复用同一份，两端不会各写一遍跑偏。
 *
 * 这里只留 Android 专属的三件事：hiltViewModel 注入、effect → 导航/Toast 分发。
 */
@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
    showBackButton: Boolean = true,
    onBack: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToCommentNotifications: () -> Unit = {},
    onNavigateToLikeNotifications: () -> Unit = {},
    onNavigateToSystemAnnouncements: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateToChat: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                MessagesEffect.NavigateToNotificationSettings -> onNavigateToNotificationSettings()
                is MessagesEffect.NavigateToChat -> onNavigateToChat(effect.userId)
                is MessagesEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    MessagesContent(
        state = state,
        onRefresh = { viewModel.sendIntent(MessagesIntent.Refresh) },
        onFriendsClick = viewModel::onFriendsClick,
        onCommentNotificationsClick = viewModel::onCommentNotificationsClick,
        onLikeNotificationsClick = viewModel::onLikeNotificationsClick,
        onSystemAnnouncementsClick = viewModel::onSystemAnnouncementsClick,
        onNotificationSettingsClick = viewModel::onNotificationSettingsClick,
        onConversationClick = viewModel::onConversationClick,
        onDeleteConversation = viewModel::deleteConversation,
        onPinConversation = viewModel::pinConversation,
        onSearchClick = onNavigateToSearch,
        modifier = modifier,
        showBackButton = showBackButton,
        onBack = viewModel::onBackClick,
    )
}
