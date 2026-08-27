package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.displayAuthorAvatar
import com.fox.music.core.model.social.displayAuthorName
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.CommentMoreBottomSheet
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCommentInputBar
import com.fox.music.core.ui.component.PostContentBody
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.core.ui.component.PostMoreBottomSheet
import com.fox.music.feature.social.viewmodel.PostDetailEffect
import com.fox.music.feature.social.viewmodel.PostDetailIntent
import com.fox.music.feature.social.viewmodel.PostDetailViewModel

const val POST_DETAIL_ROUTE = "post_detail/{postId}"

fun postDetailRoute(postId: Long) = "post_detail/$postId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
    currentUserId: Long? = null,
    onBack: () -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
    onReportPost: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
    onCommentDeleted: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val commentsPaging = state.comments.collectAsLazyPagingItems()
    val context = LocalContext.current
    var previewImages by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showCommentMoreSheet by remember { mutableStateOf(false) }
    var selectedComment by remember { mutableStateOf<Comment?>(null) }
    var showDeleteCommentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.commentsRefreshTrigger) {
        if (state.commentsRefreshTrigger > 0) {
            commentsPaging.refresh()
        }
    }

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

    previewImages?.let { images ->
        PostImagePreviewDialog(
            images = images,
            initialIndex = previewIndex,
            onDismiss = { previewImages = null },
        )
    }

    val post = state.post
    val isOwn = post?.userId == currentUserId
    if (showMoreSheet && post != null) {
        PostMoreBottomSheet(
            isOwnPost = isOwn,
            onDismiss = { showMoreSheet = false },
            onEdit = { onEditPost(post.id) },
            onDelete = { onDeletePost(post) },
            onReport = { onReportPost(post) },
        )
    }

    val commentForSheet = selectedComment
    if (showCommentMoreSheet && commentForSheet != null) {
        val isOwnComment = commentForSheet.userId == currentUserId
        CommentMoreBottomSheet(
            isOwnComment = isOwnComment,
            onDismiss = { showCommentMoreSheet = false },
            onDelete = {
                showDeleteCommentDialog = true
            },
            onReport = {
                viewModel.sendIntent(
                    PostDetailIntent.ReportComment(
                        commentId = commentForSheet.id,
                        reason = "违规内容",
                        description = null,
                    )
                )
            },
        )
    }

    if (showDeleteCommentDialog && commentForSheet != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCommentDialog = false },
            title = { Text("删除评论") },
            text = { Text("确定要删除这条评论吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCommentDialog = false
                    showCommentMoreSheet = false
                    viewModel.sendIntent(PostDetailIntent.DeleteComment(commentForSheet.id))
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCommentDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FoxTopBar(
                title = "动态详情",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    if (post != null) {
                        IconButton(onClick = { showMoreSheet = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                PostCommentInputBar(
                    value = state.commentInput,
                    onValueChange = { viewModel.sendIntent(PostDetailIntent.UpdateCommentInput(it)) },
                    onSend = { viewModel.sendIntent(PostDetailIntent.SendComment) },
                    placeholder = state.replyHint ?: "写评论...",
                    isSending = state.isCommentSending,
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingIndicator(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                useLottie = false,
            )
            post == null -> ErrorView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = "帖子不存在",
            )
            else -> {
                val pendingIds = state.pendingComments.map { it.id }.toSet()
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.sendIntent(PostDetailIntent.Refresh) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "post_content") {
                            PostContentBody(
                                post = post,
                                onPostClick = {},
                                onLikeClick = { viewModel.sendIntent(PostDetailIntent.ToggleLike) },
                                onCommentClick = {},
                                onShareClick = { onSharePost(post) },
                                onUserClick = {},
                                showMoreButton = false,
                                onImageClick = { index ->
                                    previewImages = post.images
                                    previewIndex = index
                                },
                            )
                        }
                        item(key = "comments_header") {
                            Text(
                                text = "评论",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        state.pendingComments.forEach { comment ->
                            item(key = "pending_comment_${comment.id}") {
                                PostDetailCommentItem(
                                    comment = comment,
                                    isExpanded = comment.id in state.expandedCommentIds,
                                    onToggleExpand = {
                                        viewModel.sendIntent(PostDetailIntent.ToggleExpandReplies(comment.id))
                                    },
                                    onReply = {
                                        viewModel.sendIntent(
                                            PostDetailIntent.ReplyToComment(
                                                comment.id,
                                                comment.displayAuthorName,
                                            ),
                                        )
                                    },
                                    onLongClick = {
                                        selectedComment = comment
                                        showCommentMoreSheet = true
                                    },
                                    repliesContent = if (comment.id in state.expandedCommentIds) {
                                        {
                                            PostRepliesList(
                                                rootCommentId = comment.id,
                                                viewModel = viewModel,
                                                onReply = { reply ->
                                                    viewModel.sendIntent(
                                                        PostDetailIntent.ReplyToComment(
                                                            commentId = reply.id,
                                                            userName = reply.displayAuthorName,
                                                        ),
                                                    )
                                                },
                                                onCommentLongClick = { reply ->
                                                    selectedComment = reply
                                                    showCommentMoreSheet = true
                                                },
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                        items(
                            count = commentsPaging.itemCount,
                            key = commentsPaging.itemKey { it.id },
                        ) { index ->
                            val comment = commentsPaging[index] ?: return@items
                            if (comment.id in pendingIds) return@items
                            PostDetailCommentItem(
                                comment = comment,
                                isExpanded = comment.id in state.expandedCommentIds,
                                onToggleExpand = {
                                    viewModel.sendIntent(PostDetailIntent.ToggleExpandReplies(comment.id))
                                },
                                onReply = {
                                    viewModel.sendIntent(
                                        PostDetailIntent.ReplyToComment(
                                            comment.id,
                                            comment.displayAuthorName,
                                        ),
                                    )
                                },
                                onLongClick = {
                                    selectedComment = comment
                                    showCommentMoreSheet = true
                                },
                                repliesContent = if (comment.id in state.expandedCommentIds) {
                                    {
                                        PostRepliesList(
                                            rootCommentId = comment.id,
                                            viewModel = viewModel,
                                            onReply = { reply ->
                                                viewModel.sendIntent(
                                                    PostDetailIntent.ReplyToComment(
                                                        commentId = reply.id,
                                                        userName = reply.displayAuthorName,
                                                    ),
                                                )
                                            },
                                            onCommentLongClick = { reply ->
                                                selectedComment = reply
                                                showCommentMoreSheet = true
                                            },
                                        )
                                    }
                                } else null,
                            )
                        }
                        if (commentsPaging.loadState.append is LoadState.Loading) {
                            item { LoadingIndicator(useLottie = false) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostDetailCommentItem(
    comment: Comment,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onReply: () -> Unit,
    onLongClick: () -> Unit = {},
    repliesContent: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onReply,
                    onLongClick = onLongClick,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            CachedImage(
                imageUrl = comment.displayAuthorAvatar,
                contentDescription = comment.displayAuthorName,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    text = comment.displayAuthorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (comment.replyCount > 0) {
            Text(
                text = if (isExpanded) "收起回复" else "查看 ${comment.replyCount} 条回复",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp, start = 46.dp)
                    .clickable(onClick = onToggleExpand),
            )
        }
        repliesContent?.invoke()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PostRepliesList(
    rootCommentId: Long,
    viewModel: PostDetailViewModel,
    onReply: (Comment) -> Unit,
    onCommentLongClick: (Comment) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val repliesFlow = remember(rootCommentId) { viewModel.getRepliesFlow(rootCommentId) }
    val repliesPaging = repliesFlow.collectAsLazyPagingItems()

    LaunchedEffect(state.repliesRefreshTrigger) {
        if (state.repliesRefreshTrigger > 0) {
            repliesPaging.refresh()
        }
    }

    Column(modifier = Modifier.padding(start = 46.dp, top = 8.dp)) {
        for (index in 0 until repliesPaging.itemCount) {
            val reply = repliesPaging[index] ?: continue
            PostReplyItem(
                reply = reply,
                onReply = { onReply(reply) },
                onLongClick = { onCommentLongClick(reply) },
            )
        }
        if (repliesPaging.loadState.append is LoadState.Loading) {
            LoadingIndicator(useLottie = false)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostReplyItem(
    reply: Comment,
    onReply: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onReply,
                onLongClick = onLongClick,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CachedImage(
            imageUrl = reply.displayAuthorAvatar,
            contentDescription = reply.displayAuthorName,
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                text = reply.displayAuthorName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!reply.parentAuthorName.isNullOrBlank()) {
                Text(
                    text = "回复 @${reply.parentAuthorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
