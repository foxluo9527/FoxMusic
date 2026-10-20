package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.UserProfileEffect
import com.fox.music.feature.chat.viewmodel.UserProfileIntent
import com.fox.music.feature.chat.viewmodel.UserProfileViewModel

/**
 * Android 侧「用户资料」入口：只做 ViewModel/Effect 接线，
 * 渲染交给 :feature:chat commonMain 的 [UserProfileContent]（与 iOS 同一份 UI）。
 */
@Composable
fun UserProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
    friendRequestSent: Boolean = false,
    onFriendRequestSentHandled: () -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToChat: (Long, String?, String?) -> Unit = { _, _, _ -> },
    onNavigateToAddFriend: (Long, String?) -> Unit = { _, _ -> },
    onNavigateToUserHome: (Long) -> Unit = {},
    onShareProfile: (Long, String?, String?) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sendIntent(UserProfileIntent.Load)
    }

    LaunchedEffect(friendRequestSent) {
        if (friendRequestSent) {
            viewModel.onFriendRequestSent()
            onFriendRequestSentHandled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                UserProfileEffect.NavigateBack -> onBack()
                is UserProfileEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is UserProfileEffect.NavigateToChat -> {
                    onNavigateToChat(effect.userId, effect.peerNickname, effect.peerAvatar)
                }
                is UserProfileEffect.NavigateToAddFriend -> {
                    onNavigateToAddFriend(effect.userId, effect.nickname)
                }
                is UserProfileEffect.NavigateToUserHome -> onNavigateToUserHome(effect.userId)
            }
        }
    }

    UserProfileContent(
        state = state,
        onBack = viewModel::onBackClick,
        onSendMessageClick = viewModel::onSendMessageClick,
        onAddFriendClick = viewModel::onAddFriendClick,
        onUserHomeClick = viewModel::onUserHomeClick,
        onShowRemarkDialog = { viewModel.sendIntent(UserProfileIntent.ShowRemarkDialog) },
        onDismissRemarkDialog = { viewModel.sendIntent(UserProfileIntent.DismissRemarkDialog) },
        onSubmitRemark = { viewModel.sendIntent(UserProfileIntent.SubmitRemark(it)) },
        onShareProfile = onShareProfile,
        modifier = modifier,
    )
}
