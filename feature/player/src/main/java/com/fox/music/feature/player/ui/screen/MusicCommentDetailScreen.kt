package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
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

const val MUSIC_COMMENT_DETAIL_ROUTE = "music_comment_detail/{musicId}/{commentId}"
fun musicCommentDetailRoute(musicId: Long, commentId: Long) = "music_comment_detail/$musicId/$commentId"

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
                title = { Text("评论") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                HorizontalDivider()
                ReplyInputField(
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
                modifier = Modifier
                    .fillMaxSize()
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        val refreshState = commentsPaging.loadState.refresh

        when (refreshState) {
            is LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }
            is LoadState.Error -> {
                ErrorView(
                    message = refreshState.error.message ?: "加载失败",
                    onRetry = { commentsPaging.refresh() }
                )
            }
            else -> {
                if (commentsPaging.itemCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无评论", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(commentsPaging.itemCount) { index ->
                            commentsPaging[index]?.let { comment ->
                                CommentItem(
                                    comment = comment,
                                    isExpanded = expandedComments.contains(comment.id),
                                    isLastItem = index == commentsPaging.itemCount - 1,
                                    expandedReplies = expandedReplies[comment.id] ?: emptyList(),
                                    isLoadingReplies = loadingReplies.contains(comment.id),
                                    commentLikes = commentLikes,
                                    onToggleExpand = { onToggleExpand(comment) },
                                    onLike = { onLikeComment(comment) }
                                )
                            }
                        }

                        // 加载更多状态
                        val appendState = commentsPaging.loadState.append
                        when (appendState) {
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
                                        onRetry = { commentsPaging.retry() }
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
    expandedReplies: List<Comment>,
    isLoadingReplies: Boolean,
    commentLikes: Map<Long, CommentLikeState> = emptyMap(),
    onToggleExpand: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likeState = commentLikes[comment.id]
    val isLiked = likeState?.isLiked ?: comment.isLiked
    val likeCount = likeState?.likeCount ?: comment.likeCount

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            ) {
                expandedReplies.forEach { reply ->
                    ReplyItem(reply = reply)
                }
                if (isLoadingReplies && expandedReplies.isEmpty()) {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        useLottie = false
                    )
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
private fun ReplyItem(reply: Comment, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = draftText,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入回复内容...") },
            minLines = 2,
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onSendClick(draftText) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("发送")
        }
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

