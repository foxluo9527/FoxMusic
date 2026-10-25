package com.fox.music.feature.player.ui.component

import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.feature.player.viewmodel.MusicCommentDetailEffect
import com.fox.music.feature.player.viewmodel.MusicCommentDetailIntent
import com.fox.music.feature.player.viewmodel.MusicCommentDetailViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 音乐评论底部工作表（v11 编辑排版）。
 *
 * 容器：32×4 handle + 22dp 顶圆角 + 语义 surface（浅色即 PaperSurface）；
 * 内容渲染统一走 [FoxCommentList] / [FoxCommentInputBar]，与详情页共用一套视图，
 * 避免两处回复区样式漂移。顶部间距与侧滑返回动画与 [PlaylistBottomSheet] 保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicCommentBottomSheet(
    musicId: Long,
    onDismiss: () -> Unit,
    viewModel: MusicCommentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val commentsPaging = viewModel.getCommentsPaging(musicId).collectAsLazyPagingItems()
    val context = LocalContext.current
    var selectedCommentId by remember { mutableStateOf<Long?>(null) }
    var selectedCommentUserName by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    fun dismissSheet() {
        scope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

    LaunchedEffect(state.commentsRefreshTrigger) {
        if (state.commentsRefreshTrigger > 0) {
            commentsPaging.refresh()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect {effect ->
            when(effect) {
                is MusicCommentDetailEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                MusicCommentDetailEffect.CommentSent -> {
                    commentsPaging.refresh()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissSheet() },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .onSizeChanged { sheetHeightPx = it.height.toFloat() }
            .graphicsLayer {
                val h = sheetHeightPx.takeIf { it > 0f } ?: size.height
                translationY = h * backProgress.value
                scaleX = 1f
                scaleY = 1f
            },
        sheetMaxWidth = Dp.Unspecified,
        // 语义 surface：浅色下即 PaperSurface #FFFDF9，深色下走 #1E1A15，不写死
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        dragHandle = { FoxCommentSheetHandle() },
    ) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { event ->
                    backProgress.snapTo(event.progress)
                }
                backProgress.animateTo(1f, animationSpec = tween(180))
                try {
                    sheetState.hide()
                } finally {
                    onDismiss()
                }
            } catch (e: CancellationException) {
                backProgress.animateTo(0f, animationSpec = tween(180))
                throw e
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 表头：kicker + 衬线标题 + 条数
            FoxCommentSheetHeader(commentCount = commentsPaging.itemCount)
            FoxCommentHairline()

            // 评论列表
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
                onCommentSelected = { commentId, userName ->
                    selectedCommentId = commentId
                    selectedCommentUserName = userName
                },
                onLoadMoreReplies = { commentId, currentCount ->
                    viewModel.sendIntent(MusicCommentDetailIntent.LoadMoreReplies(musicId, commentId, currentCount))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            FoxCommentHairline()

            // 常驻输入条（不再点击后弹 AlertDialog）
            FoxCommentInputBar(
                draftText = state.replyDraft,
                onDraftChange = {text ->
                    viewModel.sendIntent(MusicCommentDetailIntent.UpdateDraft(text))
                },
                onSendClick = { content ->
                    viewModel.sendIntent(
                        MusicCommentDetailIntent.PostReply(
                            musicId,
                            content,
                            selectedCommentId
                        )
                    )
                    selectedCommentId = null
                    selectedCommentUserName = null
                },
                replyToUserName = selectedCommentUserName,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    }
}
