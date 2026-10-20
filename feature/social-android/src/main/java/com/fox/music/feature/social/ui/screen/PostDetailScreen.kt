package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.core.ui.component.ReportBottomSheet
import com.fox.music.feature.social.PostDetailContent
import com.fox.music.feature.social.PostDetailEffect
import com.fox.music.feature.social.PostDetailIntent
import com.fox.music.feature.social.viewmodel.PostDetailViewModel

const val POST_DETAIL_ROUTE = "post_detail/{postId}"

fun postDetailRoute(postId: Long) = "post_detail/$postId"

/**
 * 帖子详情屏幕外壳：ViewModel + Toast + MiniPlayer（依赖 sharedTransition），
 * UI 全部交给 [:feature:social] commonMain 的 PostDetailContent。
 */
@Composable
fun PostDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
    currentUserId: Long? = null,
    isLoggedIn: Boolean = false,
    playerState: PlayerState = PlayerState(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onBack: () -> Unit = {},
    onLoginRequired: () -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
    onReportPost: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
    onCommentDeleted: () -> Unit = {},
    onPlayMusic: (Post) -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onAddMusicToPlaylist: (Music) -> Unit = {},
    onNavigateToUserHome: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val commentsPaging = viewModel.commentsPagingFlow.collectAsLazyPagingItems()
    val likesPaging = viewModel.likesPagingFlow.collectAsLazyPagingItems()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PostDetailEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is PostDetailEffect.CommentSent -> {
                    commentsPaging.refresh()
                }
                is PostDetailEffect.CommentDeleted -> {
                    onCommentDeleted()
                }
            }
        }
    }

    val showMiniPlayer = playerState.currentMusic != null &&
        sharedTransitionScope != null &&
        animatedContentScope != null

    if (state.showCommentReportSheet) {
        ReportBottomSheet(
            targetTitle = "举报",
            targetName = "评论",
            isSubmitting = state.isReportSubmitting,
            onDismiss = viewModel::dismissCommentReportSheet,
            onSubmit = { reason, description ->
                viewModel.sendIntent(PostDetailIntent.SubmitCommentReport(reason, description))
            },
        )
    }

    PostDetailContent(
        state = state,
        comments = commentsPaging,
        likes = likesPaging,
        onIntent = viewModel::sendIntent,
        modifier = modifier,
        currentUserId = currentUserId,
        isLoggedIn = isLoggedIn,
        playerState = playerState,
        repliesFlow = viewModel::getRepliesFlow,
        miniPlayer = if (showMiniPlayer) {
            {
                with(sharedTransitionScope!!) {
                    MiniPlayer(
                        playerState = playerState,
                        sharedTransitionScope = this@with,
                        animatedContentScope = animatedContentScope!!,
                        onPlayPauseClick = onTogglePlay,
                        onNextClick = onNextTrack,
                        onClick = onNavigateToPlayer,
                        modifier = Modifier,
                    )
                }
            }
        } else {
            null
        },
        onBack = onBack,
        onLoginRequired = onLoginRequired,
        onSharePost = onSharePost,
        onEditPost = onEditPost,
        onReportPost = onReportPost,
        onDeletePost = onDeletePost,
        onCommentDeleted = onCommentDeleted,
        onPlayMusic = onPlayMusic,
        onNavigateToPlayer = onNavigateToPlayer,
        onTogglePlay = onTogglePlay,
        onTagClick = onTagClick,
        onAddMusicToPlaylist = onAddMusicToPlaylist,
        onNavigateToUserHome = onNavigateToUserHome,
    )
}
