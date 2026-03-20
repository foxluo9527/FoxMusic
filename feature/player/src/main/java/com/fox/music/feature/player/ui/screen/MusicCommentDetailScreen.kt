package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.player.viewmodel.MusicCommentDetailIntent
import com.fox.music.feature.player.viewmodel.MusicCommentDetailViewModel

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

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is com.fox.music.feature.player.viewmodel.MusicCommentDetailEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CommentList(
                commentsPaging = commentsPaging,
                expandedComments = state.expandedComments,
                onToggleExpand = { commentId ->
                    viewModel.sendIntent(MusicCommentDetailIntent.ToggleExpandReplies(commentId))
                },
                onLikeComment = { cId ->
                    viewModel.sendIntent(MusicCommentDetailIntent.LikeComment(cId))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Divider()

            // 回复输入框
            ReplyInputField(
                draftText = state.replyDraft,
                onDraftChange = { text ->
                    viewModel.sendIntent(MusicCommentDetailIntent.UpdateDraft(text))
                },
                onSendClick = {
                    if (commentId > 0L) {
                        viewModel.sendIntent(
                            MusicCommentDetailIntent.PostReply(musicId, state.replyDraft, commentId)
                        )
                    } else {
                        Toast.makeText(context, "请选择要回复的评论", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CommentList(
    commentsPaging: LazyPagingItems<Comment>,
    expandedComments: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    onLikeComment: (Long) -> Unit,
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
                                    onToggleExpand = { onToggleExpand(comment.id) },
                                    onLike = { onLikeComment(comment.id) }
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
    onToggleExpand: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // 头部信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.user?.nickname ?: "匿名用户",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = comment.createdAt ?: "",
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
                    imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "点赞",
                    tint = if (comment.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = "赞${comment.likeCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "回复${comment.replyCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 展开回复按钮
        if (comment.replyCount > 0) {
            Button(
                onClick = onToggleExpand,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (isExpanded) "隐藏回复" else "展开回复",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 回复列表（当展开时显示）
        if (isExpanded && comment.replies.isNotEmpty()) {
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
                comment.replies.forEach { reply ->
                    ReplyItem(reply = reply)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        Divider(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReplyItem(reply: Comment, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.user?.nickname ?: "匿名用户",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = reply.createdAt ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${reply.likeCount}赞",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onSendClick: () -> Unit,
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
            onClick = onSendClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("发送")
        }
    }
}

