package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.social.Post
import com.fox.music.feature.social.CommunityPlazaContent
import com.fox.music.feature.social.CommunityPlazaEffect
import com.fox.music.feature.social.CommunityPlazaIntent
import com.fox.music.feature.social.viewmodel.CommunityPlazaViewModel

const val COMMUNITY_PLAZA_ROUTE = "community_plaza"

/**
 * 社区广场屏幕外壳：只负责 ViewModel + 副作用（Toast），
 * UI 全部交给 [:feature:social] commonMain 的 CommunityPlazaContent
 * —— 与 iOS 端同一份界面实现。
 */
@Composable
fun CommunityPlazaScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    currentUserId: Long? = null,
    @Suppress("UNUSED_PARAMETER") showBackButton: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit = {},
    viewModel: CommunityPlazaViewModel = hiltViewModel(),
    onSearchClick: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onPublishQueue: () -> Unit = {},
    onPostDetail: (Long) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
    onReportPost: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
    onPlayMusic: (Post) -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    playerState: PlayerState = PlayerState(),
    onTagClick: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val postPagingItems = state.posts.collectAsLazyPagingItems()
    var isUserPullRefreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(refreshTrigger) {
        viewModel.handleExternalRefreshTrigger(refreshTrigger) {
            postPagingItems.refresh()
        }
    }

    LaunchedEffect(postPagingItems.loadState.refresh) {
        if (postPagingItems.loadState.refresh !is androidx.paging.LoadState.Loading) {
            isUserPullRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CommunityPlazaEffect.NavigateToCreatePost -> onCreatePost()
                CommunityPlazaEffect.NavigateToPublishQueue -> onPublishQueue()
                is CommunityPlazaEffect.NavigateToPostDetail -> onPostDetail(effect.postId)
                is CommunityPlazaEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    CommunityPlazaContent(
        state = state.copy(isRefreshing = state.isRefreshing || isUserPullRefreshing),
        onIntent = { intent ->
            if (intent is CommunityPlazaIntent.Refresh) {
                isUserPullRefreshing = true
                viewModel.sendIntent(intent)
                postPagingItems.refresh()
            } else {
                viewModel.sendIntent(intent)
            }
        },
        modifier = modifier,
        currentUserId = currentUserId,
        onSearchClick = onSearchClick,
        onCreatePost = viewModel::onCreatePostClick,
        onPublishQueue = viewModel::onPublishQueueClick,
        onPostDetail = onPostDetail,
        onSharePost = onSharePost,
        onEditPost = onEditPost,
        onReportPost = onReportPost,
        onDeletePost = onDeletePost,
        onPlayMusic = onPlayMusic,
        onTogglePlay = onTogglePlay,
        onNavigateToPlayer = onNavigateToPlayer,
        playerState = playerState,
        onTagClick = onTagClick,
        onOpenDrawer = onOpenDrawer,
        onPostsLoaded = viewModel::onPostsLoaded,
    )
}
