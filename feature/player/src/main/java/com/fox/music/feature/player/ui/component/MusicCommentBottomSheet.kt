package com.fox.music.feature.player.ui.component

import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.displayAuthorAvatar
import com.fox.music.core.model.social.displayAuthorName
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.player.viewmodel.CommentLikeState
import com.fox.music.feature.player.viewmodel.MusicCommentDetailEffect
import com.fox.music.feature.player.viewmodel.MusicCommentDetailIntent
import com.fox.music.feature.player.viewmodel.MusicCommentDetailViewModel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 音乐评论底部工作表。
 * 顶部间距与侧滑返回动画与 [PlaylistBottomSheet] 保持一致。
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
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
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
            // 标题栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "音乐评论",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                IconButton(
                    onClick = { dismissSheet() },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(- 90f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 评论列表
            CommentList(
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

            Spacer(modifier = Modifier.height(8.dp))

            //回复输入框
            ReplyInputField(
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CommentList(
    commentsPaging: LazyPagingItems<Comment>,
    expandedComments: Set<Long>,
    expandedReplies: Map<Long, List<Comment>>,
    loadingReplies: Set<Long>,
    commentLikes: Map<Long, CommentLikeState>,
    onToggleExpand: (Comment) -> Unit,
    onLikeComment: (Comment) -> Unit,
    onCommentSelected: (Long, String) -> Unit = { _, _ -> },
    onLoadMoreReplies: (Long, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        val refreshState = commentsPaging.loadState.refresh

        when(refreshState) {
            is LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            is LoadState.Error -> {
                ErrorView(
                    message = refreshState.error.message ?: "加载失败",
                    onRetry = {commentsPaging.refresh()}
                )
            }

            else -> {
                if (commentsPaging.itemCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无评论", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(commentsPaging.itemCount) { index ->
                            commentsPaging[index]?.let { comment ->
                                CommentItem(
                                    comment = comment,
                                    isExpanded = expandedComments.contains(comment.id),
                                    isLastItem = index == commentsPaging.itemCount - 1,
                                    commentLikes = commentLikes,
                                    onToggleExpand = { onToggleExpand(comment) },
                                    onLike = { onLikeComment(comment) },
                                    onSelected = onCommentSelected,
                                    expandedReplies = expandedReplies[comment.id] ?: emptyList(),
                                    isLoadingReplies = loadingReplies.contains(comment.id),
                                    onLoadMoreReplies = onLoadMoreReplies
                                )
                            }
                        }

                        // 加载更多状态
                        val appendState = commentsPaging.loadState.append
                        when(appendState) {
                            is LoadState.Loading -> {
                                item {
                                    LoadingIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp),
                                        useLottie = true
                                    )
                                }
                            }

                            is LoadState.Error -> {
                                item {
                                    ErrorView(
                                        modifier = Modifier.fillMaxWidth(),
                                        message = "加载更多失败",
                                        showIcon = false,
                                        onRetry = {commentsPaging.retry()}
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    isExpanded: Boolean,
    isLastItem: Boolean,
    commentLikes: Map<Long, CommentLikeState> = emptyMap(),
    onToggleExpand: () -> Unit,
    onLike: () -> Unit,
    onSelected: (Long, String) -> Unit = { _, _ -> },
    expandedReplies: List<Comment> = emptyList(),
    isLoadingReplies: Boolean = false,
    onLoadMoreReplies: (Long, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val likeState = commentLikes[comment.id]
    val isLiked = likeState?.isLiked ?: comment.isLiked
    val likeCount = likeState?.likeCount ?: comment.likeCount

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelected(comment.id, comment.displayAuthorName) }
            .padding(12.dp)
    ) {
        // 主评论内容
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            CachedImage(
                imageUrl = comment.displayAuthorAvatar,
                contentDescription = comment.displayAuthorName,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = comment.displayAuthorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCommentTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 点赞按钮
            IconButton(
                onClick = onLike,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "点赞",
                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 评论内容
        Text(
            text = comment.content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // 评论统计
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "赞$likeCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "回复${comment.replyCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 展开回复按钮（未展开时显示）
        if (comment.replyCount > 0 && !isExpanded) {
            Button(
                onClick = onToggleExpand,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("展开回复", style = MaterialTheme.typography.labelSmall)
            }
        }

        // 回复列表（当展开时显示）
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(8.dp)
            ) {
                expandedReplies.forEach { reply ->
                    ReplyItem(
                        reply = reply,
                        onSelected = onSelected
                    )
                }

                if (isLoadingReplies && expandedReplies.isEmpty()) {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        useLottie = false
                    )
                }

                // 加载更多回复按钮
                if (expandedReplies.size < comment.replyCount) {
                    Button(
                        onClick = { onLoadMoreReplies(comment.id, expandedReplies.size) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        enabled = !isLoadingReplies,
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        if (isLoadingReplies) {
                            LoadingIndicator(
                                modifier = Modifier.size(18.dp),
                                useLottie = false
                            )
                        } else {
                            Text("加载更多回复", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (comment.replyCount > 0) {
                    Button(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("隐藏回复", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!isLastItem) {
            Divider(modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ReplyItem(
    reply: Comment,
    onSelected: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onSelected(reply.id, reply.displayAuthorName) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            CachedImage(
                imageUrl = reply.displayAuthorAvatar,
                contentDescription = reply.displayAuthorName,
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = reply.displayAuthorName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCommentTime(reply.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = reply.content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun ReplyInputField(
    draftText: String,
    onDraftChange: (String) -> Unit,
    onSendClick: (String) -> Unit,
    replyToUserName: String? = null,
    modifier: Modifier = Modifier,
) {
    var showInputDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf(draftText) }

    LaunchedEffect(draftText) {
        if (!showInputDialog) {
            inputText = draftText
        }
    }

    // 点击输入框时打开输入对话框
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .clickable { showInputDialog = true },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (replyToUserName != null) "回复: $replyToUserName" else "输入评论内容...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    // 输入对话框
    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = {
                Text(
                    text = if (replyToUserName != null) "回复用户" else "发表评论",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    if (replyToUserName != null) {
                        Text(
                            text = "回复: $replyToUserName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        placeholder = {
                            Text(
                                text = if (replyToUserName != null) "输入回复内容..." else "输入评论内容...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendClick(inputText)
                            onDraftChange("")
                            inputText = ""
                            showInputDialog = false
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Text("发送")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInputDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatCommentTime(createdAt: String?): String {
    if (createdAt == null) return "刚刚"
    return try {
        val postTime = ZonedDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
        val now = ZonedDateTime.now()
        val minutes = java.time.Duration.between(postTime, now).toMinutes()
        when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            minutes < 10080 -> "${minutes / 1440}天前"
            else -> postTime.format(DateTimeFormatter.ofPattern("MM-dd"))
        }
    } catch (e: Exception) {
        "刚刚"
    }
}

