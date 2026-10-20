package com.fox.music.feature.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.displayAuthorAvatar
import com.fox.music.core.model.social.displayAuthorName
import com.fox.music.core.model.user.User
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.CommentMoreBottomSheet
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCommentInputBar
import com.fox.music.core.ui.component.PostMediaCarousel
import com.fox.music.core.ui.component.PostMediaPreviewDialog
import com.fox.music.core.ui.component.PostMoreBottomSheet
import com.fox.music.core.ui.component.PostMusicBarExtended
import com.fox.music.core.ui.component.PostTagsRow
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

const val POST_DETAIL_ROUTE = "post_detail/{postId}"

fun postDetailRoute(postId: Long) = "post_detail/$postId"

/** MiniPlayer 悬浮于详情内容底部时，为列表预留的滚动间距 */
private val PostDetailMiniPlayerScrollPadding = 104.dp

private val PostDetailMediaMaxHeight = 560.dp

/**
 * 帖子详情纯界面：state + onIntent 驱动（与 Android PostDetailScreen 行为一致）。
 *
 * 评论/点赞两条分页流由宿主 collect 后以 [LazyPagingItems] 传入（Android 是
 * ViewModel 的 cachedIn flow，iOS 是 Presenter 的 Pager flow），因为
 * collectAsLazyPagingItems 必须在 Composable 里调用、且刷新动作需要拿到句柄。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostDetailContent(
    state: PostDetailState,
    comments: LazyPagingItems<Comment>,
    likes: LazyPagingItems<User>,
    onIntent: (PostDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
    currentUserId: Long? = null,
    isLoggedIn: Boolean = false,
    playerState: PlayerState = PlayerState(),
    repliesFlow: (Long) -> Flow<PagingData<Comment>> = { kotlinx.coroutines.flow.flowOf() },
    miniPlayer: (@Composable () -> Unit)? = null,
    /**
     * 底部栏额外要预留的高度。
     *
     * 用途：宿主把迷你播放条画在**页面之外**（iOS 的 SwiftUI 迷你条是常驻浮层，
     * 不像 Android 那样作为 [miniPlayer] 插槽画在本页内容里），它就会盖住评论输入栏。
     * 此时由宿主传入迷你条高度，把底栏顶上去。
     */
    bottomBarBottomInset: Dp = 0.dp,
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
    onTagClick: (String) -> Unit = {},
    onAddMusicToPlaylist: (Music) -> Unit = {},
    onNavigateToUserHome: (Long) -> Unit = {},
) {
    var previewMedia by remember { mutableStateOf<List<PostMedia>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showCommentMoreSheet by remember { mutableStateOf(false) }
    var selectedComment by remember { mutableStateOf<Comment?>(null) }
    var showDeleteCommentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.commentsRefreshTrigger) {
        if (state.commentsRefreshTrigger > 0) {
            comments.refresh()
        }
    }

    LaunchedEffect(state.likesRefreshTrigger) {
        if (state.likesRefreshTrigger > 0) {
            likes.refresh()
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
            onShare = {
                showMoreSheet = false
                if (!isLoggedIn) {
                    onLoginRequired()
                } else {
                    onSharePost(post)
                }
            },
            onEdit = { onEditPost(post.id) },
            onDelete = { onDeletePost(post) },
            onReport = {
                showMoreSheet = false
                if (!isLoggedIn) {
                    onLoginRequired()
                } else {
                    onReportPost(post)
                }
            },
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
                onIntent(PostDetailIntent.ReportComment(commentId = commentForSheet.id))
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
                    onIntent(PostDetailIntent.DeleteComment(commentForSheet.id))
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
                onAuthorClick = { post?.userId?.let(onNavigateToUserHome) },
            )
        },
        bottomBar = {
            if (post != null) {
                // 外层 Box 负责把 surface 铺满「底栏 + 预留区」，否则预留出来的那条
                // 会露出页面渐变，与底栏之间出现一道色带断层。
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    PostDetailBottomBar(
                        post = post,
                        commentInput = state.commentInput,
                        replyHint = state.replyHint,
                        isSending = state.isCommentSending,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = bottomBarBottomInset),
                        onCommentChange = { onIntent(PostDetailIntent.UpdateCommentInput(it)) },
                        onSend = { onIntent(PostDetailIntent.SendComment) },
                        onLike = {
                            if (!isLoggedIn) {
                                onLoginRequired()
                            } else {
                                onIntent(PostDetailIntent.ToggleLike)
                            }
                        },
                    )
                }
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
                val miniPlayerScrollPadding = if (miniPlayer != null) {
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
                        onRefresh = { onIntent(PostDetailIntent.Refresh) },
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
                                            onIntent(PostDetailIntent.ToggleMusicFavorite)
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
                                        onSelectTab = { onIntent(PostDetailIntent.SelectFeedTab(it)) },
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
                                            onUserClick = onNavigateToUserHome,
                                            onToggleExpand = {
                                                onIntent(
                                                    PostDetailIntent.ToggleExpandReplies(comment.id),
                                                )
                                            },
                                            onReply = {
                                                onIntent(
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
                                                        repliesFlow = repliesFlow,
                                                        refreshTrigger = state.repliesRefreshTrigger,
                                                        onUserClick = onNavigateToUserHome,
                                                        onReply = { reply ->
                                                            onIntent(
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
                                    count = comments.itemCount,
                                    key = comments.itemKey { it.id },
                                ) { index ->
                                    val comment = comments[index] ?: return@items
                                    if (comment.id in pendingIds) return@items
                                    PostDetailCommentItem(
                                        comment = comment,
                                        isExpanded = comment.id in state.expandedCommentIds,
                                        onUserClick = onNavigateToUserHome,
                                        onToggleExpand = {
                                            onIntent(
                                                PostDetailIntent.ToggleExpandReplies(comment.id),
                                            )
                                        },
                                        onReply = {
                                            onIntent(
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
                                                    repliesFlow = repliesFlow,
                                                    refreshTrigger = state.repliesRefreshTrigger,
                                                    onUserClick = onNavigateToUserHome,
                                                    onReply = { reply ->
                                                        onIntent(
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
                                if (comments.loadState.append is LoadState.Loading) {
                                    item(key = "comments_loading") {
                                        LoadingIndicator(useLottie = false)
                                    }
                                }
                            } else if (state.canViewLikeList) {
                                if (likes.loadState.refresh is LoadState.Loading &&
                                    likes.itemCount == 0
                                ) {
                                    item(key = "likes_refreshing") {
                                        LoadingIndicator(useLottie = false)
                                    }
                                } else if (likes.itemCount == 0 &&
                                    likes.loadState.refresh !is LoadState.Loading
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
                                    count = likes.itemCount,
                                    key = likes.itemKey { it.id },
                                ) { index ->
                                    val liker = likes[index] ?: return@items
                                    PostLikerRow(
                                        user = liker,
                                        onClick = { onNavigateToUserHome(liker.id) },
                                    )
                                }
                                if (likes.loadState.append is LoadState.Loading) {
                                    item(key = "likes_loading") {
                                        LoadingIndicator(useLottie = false)
                                    }
                                }
                            }
                        }
                    }
                    if (miniPlayer != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            miniPlayer()
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
    onAuthorClick: () -> Unit,
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAuthorClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                )
            }
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostDetailCommentItem(
    comment: Comment,
    isExpanded: Boolean,
    onUserClick: (Long) -> Unit,
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
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            CachedImage(
                imageUrl = comment.displayAuthorAvatar,
                contentDescription = comment.displayAuthorName,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onUserClick(comment.userId) },
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = onReply,
                        onLongClick = onLongClick,
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = comment.displayAuthorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onUserClick(comment.userId) },
                    )
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (!isExpanded && previewReply != null) {
            PostReplyItem(
                reply = previewReply,
                onUserClick = onUserClick,
                onReply = onReply,
                onLongClick = onLongClick,
                modifier = Modifier.padding(start = 46.dp, top = 8.dp),
            )
        }
        if (comment.replyCount > 0 && !isExpanded) {
            val remaining = if (previewReply != null) {
                (comment.replyCount - 1).coerceAtLeast(0)
            } else {
                comment.replyCount
            }
            val label = if (remaining > 0) {
                "展开 $remaining 条回复"
            } else {
                "展开回复"
            }
            if (remaining > 0 || previewReply == null) {
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
        if (isExpanded) {
            repliesContent?.invoke()
            if (comment.replyCount > 0) {
                Text(
                    text = "收起回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 46.dp)
                        .clickable(onClick = onToggleExpand),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PostRepliesList(
    rootCommentId: Long,
    repliesFlow: (Long) -> Flow<PagingData<Comment>>,
    refreshTrigger: Int,
    onUserClick: (Long) -> Unit,
    onReply: (Comment) -> Unit,
    onCommentLongClick: (Comment) -> Unit = {},
) {
    val repliesFlowState = remember(rootCommentId) { repliesFlow(rootCommentId) }
    val repliesPaging = repliesFlowState.collectAsLazyPagingItems()

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            repliesPaging.refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, top = 8.dp),
    ) {
        for (index in 0 until repliesPaging.itemCount) {
            val reply = repliesPaging[index] ?: continue
            PostReplyItem(
                reply = reply,
                onUserClick = onUserClick,
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
    onUserClick: (Long) -> Unit,
    onReply: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CachedImage(
            imageUrl = reply.displayAuthorAvatar,
            contentDescription = reply.displayAuthorName,
            modifier = Modifier
                .size(28.dp)
                .clickable { onUserClick(reply.userId) },
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = onReply,
                    onLongClick = onLongClick,
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = reply.displayAuthorName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onUserClick(reply.userId) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = user.nickname?.takeIf { it.isNotBlank() }
        ?: user.username.takeIf { it.isNotBlank() }
        ?: "用户${user.id}"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        val local = Instant.parse(createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.monthNumber.pad2()}-${local.dayOfMonth.pad2()} " +
            "${local.hour.pad2()}:${local.minute.pad2()}"
    } catch (_: Exception) {
        createdAt
    }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()
