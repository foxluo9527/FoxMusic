package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.user.User
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.displayAuthorAvatar
import com.fox.music.core.model.social.displayAuthorName
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.CommentMoreBottomSheet
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.core.ui.component.PostCommentInputBar
import com.fox.music.core.ui.component.PostMediaCarousel
import com.fox.music.core.ui.component.PostMediaPreviewDialog
import com.fox.music.core.ui.component.PostMoreBottomSheet
import com.fox.music.core.ui.component.PostMusicBarExtended
import com.fox.music.core.ui.component.PostTagsRow
import com.fox.music.feature.social.viewmodel.PostDetailEffect
import com.fox.music.feature.social.viewmodel.PostDetailFeedTab
import com.fox.music.feature.social.viewmodel.PostDetailIntent
import com.fox.music.feature.social.viewmodel.PostDetailViewModel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val POST_DETAIL_ROUTE = "post_detail/{postId}"

fun postDetailRoute(postId: Long) = "post_detail/$postId"

/** MiniPlayer 悬浮于详情内容底部时，为列表预留的滚动间距 */
private val PostDetailMiniPlayerScrollPadding = 104.dp

private val PostDetailMediaMaxHeight = 560.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
    currentUserId: Long? = null,
    playerState: PlayerState = PlayerState(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onBack: () -> Unit = {},
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
) {
    val state by viewModel.uiState.collectAsState()
    val commentsPaging = viewModel.commentsPagingFlow.collectAsLazyPagingItems()
    val likesPaging = viewModel.likesPagingFlow.collectAsLazyPagingItems()
    val context = LocalContext.current
    var previewMedia by remember { mutableStateOf<List<PostMedia>?>(null) }
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

    LaunchedEffect(state.likesRefreshTrigger) {
        if (state.likesRefreshTrigger > 0) {
            likesPaging.refresh()
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

    previewMedia?.let { media ->
        PostMediaPreviewDialog(
            media = media,
            initialIndex = previewIndex,
            onDismiss = { previewMedia = null },
        )
    }

    val post = state.post
    val isOwn = post?.userId == currentUserId
    if (showMoreSheet && post != null) {
        PostMoreBottomSheet(
            isOwnPost = isOwn,
            onDismiss = { showMoreSheet = false },
            onShare = { onSharePost(post) },
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
            PostDetailTopBar(
                post = post,
                onBack = onBack,
                onMore = { showMoreSheet = true },
            )
        },
        bottomBar = {
            if (post != null) {
                PostDetailBottomBar(
                    post = post,
                    commentInput = state.commentInput,
                    replyHint = state.replyHint,
                    isSending = state.isCommentSending,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                    onCommentChange = {
                        viewModel.sendIntent(PostDetailIntent.UpdateCommentInput(it))
                    },
                    onSend = { viewModel.sendIntent(PostDetailIntent.SendComment) },
                    onLike = { viewModel.sendIntent(PostDetailIntent.ToggleLike) },
                    onFavorite = { viewModel.sendIntent(PostDetailIntent.ToggleFavorite) },
                )
            }
        },
    ) { innerPadding ->
        val showMiniPlayer = playerState.currentMusic != null &&
            sharedTransitionScope != null &&
            animatedContentScope != null
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
                val miniPlayerScrollPadding = if (showMiniPlayer) {
                    PostDetailMiniPlayerScrollPadding
                } else {
                    0.dp
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.sendIntent(PostDetailIntent.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = miniPlayerScrollPadding),
                        ) {
                        item(key = "media") {
                            if (post.displayMedia.isNotEmpty()) {
                                PostMediaCarousel(
                                    media = post.displayMedia,
                                    cornerRadius = 0.dp,
                                    maxHeight = PostDetailMediaMaxHeight,
                                    onMediaClick = { index ->
                                        previewMedia = post.displayMedia
                                        previewIndex = index
                                    },
                                )
                            }
                        }
                        post.music?.let { music ->
                            val isCurrentTrack = playerState.currentMusic?.id == music.id
                            val isPlaying = isCurrentTrack && playerState.isPlaying
                            stickyHeader(key = "music") {
                                PostMusicBarExtended(
                                    music = music,
                                    isCurrentTrack = isCurrentTrack,
                                    isPlaying = isPlaying,
                                    onRowClick = {
                                        onPlayMusic(post)
                                        onNavigateToPlayer()
                                    },
                                    onPlayClick = {
                                        if (isCurrentTrack) {
                                            onTogglePlay()
                                        } else {
                                            onPlayMusic(post)
                                        }
                                    },
                                    onFavoriteClick = {
                                        viewModel.sendIntent(PostDetailIntent.ToggleMusicFavorite)
                                    },
                                    onAddToPlaylistClick = { onAddMusicToPlaylist(music) },
                                )
                                HorizontalDivider()
                            }
                        }
                        item(key = "body") {
                            PostDetailBody(
                                post = post,
                                onTagClick = onTagClick,
                            )
                        }
                        item(key = "feed_tabs") {
                            if (state.canViewLikeList) {
                                PostDetailFeedTabs(
                                    selectedTab = state.feedTab,
                                    commentCount = post.commentCount,
                                    likeCount = post.likeCount,
                                    onSelectTab = {
                                        viewModel.sendIntent(PostDetailIntent.SelectFeedTab(it))
                                    },
                                )
                            } else {
                                Text(
                                    text = "评论 ${post.commentCount}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        if (!state.canViewLikeList || state.feedTab == PostDetailFeedTab.COMMENTS) {
                        state.pendingComments.forEach { comment ->
                            item(key = "pending_comment_${comment.id}") {
                                PostDetailCommentItem(
                                    comment = comment,
                                    isExpanded = comment.id in state.expandedCommentIds,
                                    onToggleExpand = {
                                        viewModel.sendIntent(
                                            PostDetailIntent.ToggleExpandReplies(comment.id),
                                        )
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
                                    viewModel.sendIntent(
                                        PostDetailIntent.ToggleExpandReplies(comment.id),
                                    )
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
                            item(key = "comments_loading") {
                                LoadingIndicator(useLottie = false)
                            }
                        }
                        } else if (state.canViewLikeList) {
                            if (likesPaging.loadState.refresh is LoadState.Loading &&
                                likesPaging.itemCount == 0
                            ) {
                                item(key = "likes_refreshing") {
                                    LoadingIndicator(useLottie = false)
                                }
                            } else if (likesPaging.itemCount == 0 &&
                                likesPaging.loadState.refresh !is LoadState.Loading
                            ) {
                                item(key = "likes_empty") {
                                    Text(
                                        text = "暂无点赞",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                    )
                                }
                            }
                            items(
                                count = likesPaging.itemCount,
                                key = likesPaging.itemKey { it.id },
                            ) { index ->
                                val liker = likesPaging[index] ?: return@items
                                PostLikerRow(user = liker)
                            }
                            if (likesPaging.loadState.append is LoadState.Loading) {
                                item(key = "likes_loading") {
                                    LoadingIndicator(useLottie = false)
                                }
                            }
                        }
                        }
                    }
                    if (showMiniPlayer) {
                        with(sharedTransitionScope) {
                            MiniPlayer(
                                playerState = playerState,
                                sharedTransitionScope = this@with,
                                animatedContentScope = animatedContentScope,
                                onPlayPauseClick = onTogglePlay,
                                onNextClick = onNextTrack,
                                onClick = onNavigateToPlayer,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostDetailTopBar(
    post: Post?,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        if (post != null) {
            CachedImage(
                imageUrl = post.authorAvatar,
                contentDescription = post.authorName,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = post.authorName ?: "用户",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PostDetailBody(
    post: Post,
    onTagClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        val title = post.title?.takeIf { it.isNotBlank() && it != "无标题" }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
        }
        val bodyText = post.content.trim().takeIf { content ->
            content.isNotBlank() && content != title
        }
        if (bodyText != null) {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (post.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PostTagsRow(tags = post.tags.map { it.name }, onTagClick = onTagClick)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = formatPostDetailTime(post.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostDetailBottomBar(
    post: Post,
    commentInput: String,
    replyHint: String?,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PostCommentInputBar(
                    value = commentInput,
                    onValueChange = onCommentChange,
                    onSend = onSend,
                    placeholder = replyHint ?: "说点什么...",
                    isSending = isSending,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onLike)
                    .padding(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "点赞",
                    tint = if (post.isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = post.likeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onFavorite)
                    .padding(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = if (post.isFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (post.isFavorited) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "收藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    val previewReply = comment.replies.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
        if (!isExpanded && previewReply != null) {
            PostReplyItem(
                reply = previewReply,
                onReply = onReply,
                onLongClick = onLongClick,
                modifier = Modifier.padding(start = 46.dp, top = 8.dp),
            )
        }
        if (comment.replyCount > 0) {
            val remaining = if (!isExpanded && previewReply != null) {
                (comment.replyCount - 1).coerceAtLeast(0)
            } else {
                comment.replyCount
            }
            val label = when {
                isExpanded -> "收起回复"
                remaining > 0 -> "展开 $remaining 条回复"
                else -> "展开回复"
            }
            if (isExpanded || remaining > 0 || previewReply == null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 46.dp)
                        .clickable(onClick = onToggleExpand),
                )
            }
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

@Composable
private fun PostDetailFeedTabs(
    selectedTab: PostDetailFeedTab,
    commentCount: Int,
    likeCount: Int,
    onSelectTab: (PostDetailFeedTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostDetailFeedTabItem(
            text = "评论 $commentCount",
            selected = selectedTab == PostDetailFeedTab.COMMENTS,
            onClick = { onSelectTab(PostDetailFeedTab.COMMENTS) },
        )
        PostDetailFeedTabItem(
            text = "点赞 $likeCount",
            selected = selectedTab == PostDetailFeedTab.LIKES,
            onClick = { onSelectTab(PostDetailFeedTab.LIKES) },
        )
    }
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun PostDetailFeedTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PostLikerRow(
    user: User,
    modifier: Modifier = Modifier,
) {
    val displayName = user.nickname?.takeIf { it.isNotBlank() }
        ?: user.username.takeIf { it.isNotBlank() }
        ?: "用户${user.id}"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = user.avatar,
            contentDescription = displayName,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatPostDetailTime(createdAt: String?): String {
    if (createdAt == null) return ""
    return try {
        val postTime = ZonedDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
        postTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
        createdAt
    }
}
