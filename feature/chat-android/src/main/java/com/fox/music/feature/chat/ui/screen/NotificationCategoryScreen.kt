package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.NotificationCategoryEffect
import com.fox.music.feature.chat.viewmodel.NotificationCategoryIntent
import com.fox.music.feature.chat.viewmodel.NotificationCategoryViewModel

/**
 * Android 侧通知分类入口：只做 ViewModel/Effect 接线，
 * 渲染交给 :feature:chat commonMain 的 [NotificationCategoryContent]。
 */
@Composable
fun NotificationCategoryScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationCategoryViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToPostDetail: (Long) -> Unit = {},
    onNavigateToMusicComment: (Long, Long) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sendIntent(NotificationCategoryIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                NotificationCategoryEffect.NavigateBack -> onBack()
                is NotificationCategoryEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is NotificationCategoryEffect.NavigateToPostDetail -> {
                    onNavigateToPostDetail(effect.postId)
                }
                is NotificationCategoryEffect.NavigateToMusicComment -> {
                    onNavigateToMusicComment(effect.musicId, effect.commentId)
                }
            }
        }
    }

    NotificationCategoryContent(
        state = state,
        onBack = viewModel::onBackClick,
        onNotificationClick = viewModel::onNotificationClick,
        modifier = modifier,
    )
}
