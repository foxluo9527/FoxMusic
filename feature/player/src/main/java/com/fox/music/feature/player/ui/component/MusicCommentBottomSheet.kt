package com.fox.music.feature.player.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.social.Comment
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.player.viewmodel.MusicCommentDetailEffect
import com.fox.music.feature.player.viewmodel.MusicCommentDetailIntent
import com.fox.music.feature.player.viewmodel.MusicCommentDetailViewModel

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

    LaunchedEffect(Unit) {
        viewModel.effect.collect {effect ->
            when(effect) {
                is MusicCommentDetailEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        sheetMaxWidth = Dp.Unspecified,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
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
                    onClick = onDismiss,
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
                onToggleExpand = { commentId ->
                    viewModel.sendIntent(MusicCommentDetailIntent.ToggleExpandReplies(commentId))
                },
                onLikeComment = { cId ->
                    viewModel.sendIntent(MusicCommentDetailIntent.LikeComment(cId))
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
                onSendClick = {
                    viewModel.sendIntent(
                        MusicCommentDetailIntent.PostReply(
                            musicId,
                            state.replyDraft,
                            selectedCommentId ?: 0L
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
    onToggleExpand: (Long) -> Unit,
    onLikeComment: (Long) -> Unit,
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
                                    onToggleExpand = { onToggleExpand(comment.id) },
                                    onLike = { onLikeComment(comment.id) },
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
    onToggleExpand: () -> Unit,
    onLike: () -> Unit,
    onSelected: (Long, String) -> Unit = { _, _ -> },
    expandedReplies: List<Comment> = emptyList(),
    isLoadingReplies: Boolean = false,
    onLoadMoreReplies: (Long, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelected(comment.id, comment.user?.nickname ?: "匿名用户") }
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
                    fontWeight = FontWeight.Bold
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
        if (isExpanded && expandedReplies.isNotEmpty()) {
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
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
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
            }
        }

        Divider(modifier = Modifier.padding(top = 12.dp))
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
            .clickable { onSelected(reply.id, reply.user?.nickname ?: "匿名用户") }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.user?.nickname ?: "匿名用户",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
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
    replyToUserName: String? = null,
    modifier: Modifier = Modifier,
) {
    var showInputDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf(draftText) }

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
                            onDraftChange(inputText)
                            onSendClick()
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

