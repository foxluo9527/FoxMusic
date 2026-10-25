package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.feature.player.ui.component.FoxCommentHairline
import com.fox.music.feature.player.ui.component.FoxCommentInputBar
import com.fox.music.feature.player.ui.component.FoxCommentList
import com.fox.music.feature.player.ui.component.FoxCommentSheetHeader
import com.fox.music.feature.player.viewmodel.MusicCommentDetailEffect
import com.fox.music.feature.player.viewmodel.MusicCommentDetailIntent
import com.fox.music.feature.player.viewmodel.MusicCommentDetailViewModel

const val MUSIC_COMMENT_DETAIL_ROUTE = "music_comment_detail/{musicId}/{commentId}"
fun musicCommentDetailRoute(musicId: Long, commentId: Long) = "music_comment_detail/$musicId/$commentId"

/**
 * 音乐评论详情页（v11 编辑排版）。
 *
 * 渲染与底部弹窗共用 [FoxCommentList] / [FoxCommentInputBar]，
 * 楼中楼缩进与引导竖条两处完全一致；ViewModel 契约与 Paging3 分页行为不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicCommentDetailScreen(
    modifier: Modifier = Modifier,
    musicId: Long,
    commentId: Long,
    navController: NavHostController,
    viewModel: MusicCommentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val commentsPaging = viewModel.getCommentsPaging(musicId).collectAsLazyPagingItems()
    val context = LocalContext.current

    LaunchedEffect(state.commentsRefreshTrigger) {
        if (state.commentsRefreshTrigger > 0) {
            commentsPaging.refresh()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MusicCommentDetailEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                MusicCommentDetailEffect.CommentSent -> {
                    commentsPaging.refresh()
                }
            }
        }
    }

    var hasAutoExpanded by remember(commentId) { mutableStateOf(false) }
    LaunchedEffect(commentsPaging.loadState.refresh, commentsPaging.itemCount, commentId) {
        if (commentId <= 0 || hasAutoExpanded) return@LaunchedEffect
        if (commentsPaging.loadState.refresh is LoadState.Loading) return@LaunchedEffect
        for (index in 0 until commentsPaging.itemCount) {
            commentsPaging[index]?.takeIf { it.id == commentId }?.let { comment ->
                viewModel.sendIntent(
                    MusicCommentDetailIntent.ToggleExpandReplies(
                        commentId = comment.id,
                        embeddedReplies = comment.replies,
                        replyCount = comment.replyCount,
                    )
                )
                hasAutoExpanded = true
                break
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "音乐评论",
                        style = FoxEditorial.heroTitle.copy(
                            fontSize = 19.sp,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                HorizontalDivider(color = PaperHairline)
                FoxCommentInputBar(
                    draftText = state.replyDraft,
                    onDraftChange = { text ->
                        viewModel.sendIntent(MusicCommentDetailIntent.UpdateDraft(text))
                    },
                    onSendClick = { content ->
                        if (commentId > 0L) {
                            viewModel.sendIntent(
                                MusicCommentDetailIntent.PostReply(musicId, content, commentId)
                            )
                        } else {
                            Toast.makeText(context, "请选择要回复的评论", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FoxCommentSheetHeader(commentCount = commentsPaging.itemCount)
            FoxCommentHairline()
            FoxCommentList(
                commentsPaging = commentsPaging,
                expandedComments = state.expandedComments,
                expandedReplies = state.expandedReplies,
                loadingReplies = state.loadingReplies,
                commentLikes = state.commentLikes,
                onToggleExpand = { comment ->
                    viewModel.sendIntent(
                        MusicCommentDetailIntent.ToggleExpandReplies(
                            commentId = comment.id,
                            embeddedReplies = comment.replies,
                            replyCount = comment.replyCount,
                        )
                    )
                },
                onLikeComment = { comment ->
                    val likeState = state.commentLikes[comment.id]
                    viewModel.sendIntent(
                        MusicCommentDetailIntent.LikeComment(
                            commentId = comment.id,
                            isLiked = likeState?.isLiked ?: comment.isLiked,
                            likeCount = likeState?.likeCount ?: comment.likeCount,
                        )
                    )
                },
                onLoadMoreReplies = { commentId, currentCount ->
                    viewModel.sendIntent(
                        MusicCommentDetailIntent.LoadMoreReplies(musicId, commentId, currentCount)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
