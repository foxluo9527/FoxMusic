package com.fox.music.feature.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxEditorialSectionLabel
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostMediaCarousel
import com.fox.music.core.ui.component.PostMediaPreviewDialog
import com.fox.music.core.ui.component.PostMoreBottomSheet
import com.fox.music.core.ui.component.PostMusicBarExtended
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkMuted
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.foxEditorialAccent
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarIconAction
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
        FoxDialog(
            title = "删除评论",
            text = "确定要删除这条评论吗？",
            confirmText = "删除",
            onDismiss = { showDeleteCommentDialog = false },
            onConfirm = {
                showDeleteCommentDialog = false
                showCommentMoreSheet = false
                onIntent(PostDetailIntent.DeleteComment(commentForSheet.id))
            },
            destructive = true,
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 迷你播放条纳入底栏栈、排在输入条上方：它原本是画在 content 里的浮层，
                        // 一旦页面底部还有系统导航栏/宿主浮层，就会压住评论输入条。
                        // 放进底栏后输入条永远在最底部，完整可见。
                        if (miniPlayer != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                miniPlayer()
                            }
                        }
                        PostDetailBottomBar(
                            commentInput = state.commentInput,
                            replyHint = state.replyHint,
                            isSending = state.isCommentSending,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(bottom = bottomBarBottomInset),
                            onCommentChange = { onIntent(PostDetailIntent.UpdateCommentInput(it)) },
                            onSend = { onIntent(PostDetailIntent.SendComment) },
                        )
                    }
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
                                    onLike = {
                                        if (!isLoggedIn) {
                                            onLoginRequired()
                                        } else {
                                            onIntent(PostDetailIntent.ToggleLike)
                                        }
                                    },
                                    onShare = {
                                        if (!isLoggedIn) {
                                            onLoginRequired()
                                        } else {
                                            onSharePost(post)
                                        }
                                    },
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
                                    FoxEditorialSectionLabel(
                                        number = "02",
                                        title = "评论",
                                        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                                        trailing = {
                                            Text(
                                                text = post.commentCount.toString(),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                ),
                                                color = InkQuaternary,
                                            )
                                        },
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
                                if (comments.itemCount == 0 &&
                                    state.pendingComments.isEmpty() &&
                                    comments.loadState.refresh !is LoadState.Loading
                                ) {
                                    item(key = "comments_empty") { PostDetailCommentsEmpty() }
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
    FoxTopBar(
        showBackButton = true,
        onBackClick = onBack,
        // 帖子详情的顶栏是「图上方的一条纸色栏」，不是压在图片上：
        // 传 onMedia 会给返回键垫一个半透明黑圆底，在浅底上就是一个多余的灰圆圈。
        titleContent = {
            if (post != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onAuthorClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = post.authorAvatar,
                        contentDescription = post.authorName,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        placeholderIcon = Icons.Default.Person,
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            text = post.authorName ?: "用户",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatPostDetailTime(post.createdAt).substringBefore(" "),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp,
                            ),
                            color = InkQuaternary,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        actions = {
            if (post != null) {
                FoxTopBarIconAction(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = "更多",
                    onClick = onMore,
                    tint = InkMuted,
                )
            }
        },
    )
}

@Composable
private fun PostDetailBody(
    post: Post,
    onTagClick: (String) -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        val title = post.title?.takeIf { it.isNotBlank() && it != "无标题" }
        if (title != null) {
            Text(
                text = title,
                style = FoxEditorial.articleTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
        }
        val bodyText = post.content.trim().takeIf { content ->
            content.isNotBlank() && content != title
        }
        if (bodyText != null) {
            Text(
                text = bodyText,
                // 14.5sp / 行高 1.72 ≈ 24.8sp：与 22sp 衬线标题拉开层级
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 24.8.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (post.tags.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                post.tags.forEach { tag ->
                    Text(
                        text = "#${tag.name}",
                        // 设计稿：话题标签取 --accent（陶土 #B8513A），不随用户主题预设漂移
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                        ),
                        color = foxEditorialAccent,
                        modifier = Modifier.clickable { onTagClick(tag.name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = formatPostDetailTime(post.createdAt),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
            ),
            color = InkTertiary,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = PaperHairline)
        PostDetailInteractionRow(
            post = post,
            onLike = onLike,
            onShare = onShare,
        )
        HorizontalDivider(color = PaperHairline)
    }
}

/**
 * 互动行：赞 / 评论 / 分享。线性 SVG（禁 emoji），与正文之间用 1px 发丝线隔开。
 * 强调色只出现在「已赞」这一个选中态上。
 */
@Composable
private fun PostDetailInteractionRow(
    post: Post,
    onLike: () -> Unit,
    onShare: () -> Unit,
) {
    val accent = foxEditorialAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostDetailInteractionItem(
            icon = Icons.Outlined.Favorite,
            contentDescription = if (post.isLiked) "取消赞" else "赞",
            text = post.likeCount.toString(),
            tint = if (post.isLiked) accent else InkTertiary,
            onClick = onLike,
        )
        PostDetailInteractionItem(
            icon = Icons.Outlined.ModeComment,
            contentDescription = "评论",
            text = post.commentCount.toString(),
            tint = InkTertiary,
        )
        PostDetailInteractionItem(
            icon = Icons.Outlined.Share,
            contentDescription = "分享",
            text = "分享",
            tint = InkTertiary,
            onClick = onShare,
        )
    }
}

@Composable
private fun PostDetailInteractionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    text: String,
    tint: Color,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            color = tint,
        )
    }
}

@Composable
private fun PostDetailBottomBar(
    commentInput: String,
    replyHint: String?,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val pillShape = RoundedCornerShape(19.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = commentInput,
            onValueChange = onCommentChange,
            modifier = Modifier
                .weight(1f)
                .height(38.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(foxEditorialAccent),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, PaperHairline, pillShape)
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (commentInput.isEmpty()) {
                        Text(
                            text = replyHint ?: "说点什么…",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            ),
                            color = InkQuaternary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(44.dp)
                .clickable(enabled = !isSending && commentInput.isNotBlank()) { onSend() }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isSending) "发送中" else "发送",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                // 设计稿：CTA 取 --accent（陶土 #B8513A）
                color = foxEditorialAccent,
            )
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
                    .size(32.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.displayAuthorName,
                            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { onUserClick(comment.userId) },
                        )
                        val date = formatPostDetailTime(comment.createdAt).substringBefore(" ")
                        if (date.isNotBlank()) {
                            Text(
                                text = "· $date",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                ),
                                color = InkQuaternary,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 20.9.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 3.dp),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
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
    HorizontalDivider(color = PaperHairline)
}

/**
 * 分类下的「评论 / 点赞」切换项。
 *
 * v12 修：原来这条用了 `Box(width = IntrinsicSize.Min)` 撑下划线的写法，
 * 而 `IntrinsicSize.Min` 遇到 CJK 文本时最小固有宽度会塌到接近 0（中文每个字
 * 都是断行点，minIntrinsicWidth 只保证「不断行」不保证「不拆词」），父 Box 被
 * 压窄后相邻的「点赞 3」就被挤成竖排单字——上一次的修法只加了宽度约束，
 * 治标不治本。
 *
 * 现改为：**不做固有尺寸测量**，文本 `maxLines = 1 + softWrap = false` 强约束单行，
 * 下划线用 `drawBehind` 按自身实测宽度画，天然跟随文字宽度，也不需要外层 Box。
 */
@Composable
private fun PostDetailFeedTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = foxEditorialAccent
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            // drawBehind 放在 padding 之前：这样量到的是「文字 + 上下留白」的外框，
            // 下划线才能贴在整个 tab 的底边而不是紧贴文字。
            .drawBehind {
                if (selected) {
                    val thickness = 2.dp.toPx()
                    drawRect(
                        color = accent,
                        topLeft = Offset(0f, size.height - thickness),
                        size = Size(size.width, thickness),
                    )
                }
            }
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            ),
            color = if (selected) accent else InkTertiary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 评论空态（v12 新增）。
 *
 * 之前评论为空时这里什么都不渲染，Tab 下方直接是一片空白，看起来像页面坏了。
 * 空态给一个装饰性引号 + 一句引导，不做插画、不加按钮。
 */
@Composable
private fun PostDetailCommentsEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "\u201C",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 40.sp,
                lineHeight = 40.sp,
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "还没有评论",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "说点什么\n让这首歌被更多人听见",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
            ),
            color = InkQuaternary,
            textAlign = TextAlign.Center,
        )
    }
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
