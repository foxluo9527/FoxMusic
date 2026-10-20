package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.FriendsEffect
import com.fox.music.feature.chat.viewmodel.FriendsIntent
import com.fox.music.feature.chat.viewmodel.FriendsViewModel

/**
 * Android 侧「我的好友」入口：只做 ViewModel/Effect 接线，
 * 渲染交给 :feature:chat commonMain 的 [FriendsContent]（与 iOS 同一份 UI）。
 */
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToUserProfile: (
        userId: Long,
        nickname: String?,
        avatar: String?,
        signature: String?,
        isFriend: Boolean,
        isRequested: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sendIntent(FriendsIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                FriendsEffect.NavigateBack -> onBack()
                FriendsEffect.NavigateToSearch -> onNavigateToSearch()
                is FriendsEffect.NavigateToUserProfile -> {
                    onNavigateToUserProfile(
                        effect.userId,
                        effect.nickname,
                        effect.avatar,
                        effect.signature,
                        effect.isFriend,
                        effect.isRequested,
                    )
                }
                is FriendsEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    FriendsContent(
        state = state,
        onBack = viewModel::onBackClick,
        onAddFriendClick = viewModel::onAddFriendClick,
        onFriendClick = viewModel::onFriendClick,
        onAcceptRequest = { viewModel.sendIntent(FriendsIntent.AcceptRequest(it)) },
        modifier = modifier,
    )
}
