package com.fox.music.feature.social

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCommentInputBar
import com.fox.music.core.ui.component.PostMediaPreviewDialog
import com.fox.music.core.ui.component.PostNoteGridCard
import com.fox.music.core.ui.component.FoxCircleIconButton
import com.fox.music.core.ui.component.FoxPageHeader
import com.fox.music.core.ui.theme.FoxSpacing

const val COMMUNITY_PLAZA_ROUTE = "community_plaza"

/**
 * 社区广场纯界面：state + onIntent 驱动（与 Android CommunityPlazaScreen 行为一致）。
 *
 * 分页列表由 [CommunityPlazaState.posts] 驱动；点赞走 [CommunityPlazaState.likeOverrides]
 * 叠加（分页数据不可变，所以渲染时用 [applyLikeOverride] 合成展示值）。
 * Toast / 导航等副作用由宿主处理（Android 消费 effect，iOS 直接回调）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPlazaContent(
    state: CommunityPlazaState,
    onIntent: (CommunityPlazaIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    currentUserId: Long? = null,
    onSearchClick: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onPublishQueue: () -> Unit = {},
    onPostDetail: (Long) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
    onReportPost: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
    onPlayMusic: (Post) -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    playerState: PlayerState = PlayerState(),
    onTagClick: (String) -> Unit = {},
    /** 分页首屏到手：回传给宿主落快照（下次进页面先渲染这批，见 [CommunityPlazaState.cachedPosts]）。 */
    onPostsLoaded: (List<Post>) -> Unit = {},
) {
    val postPagingItems = state.posts.collectAsLazyPagingItems()
    val layoutMode = state.layoutMode
    // 缓存优先：分页空窗期用上一次的帖子顶上
    val cachedPosts = state.cachedPosts
    val postCount = postPagingItems.itemCount.takeIf { it > 0 } ?: cachedPosts.size

    // 必须现读 itemCount 再取：LazyPagingItems.get(index) 在 index >= itemCount 时
    // 直接抛 IndexOutOfBoundsException（刷新换新 PagingData、首屏未回时 itemCount 为 0，
    // 而 count 可能还是按缓存算出来的 → get(0) 崩）。
    fun postAt(index: Int): Post? =
        if (index < postPagingItems.itemCount) {
            postPagingItems[index]
        } else {
            cachedPosts.getOrNull(index)
        }

    // key 带来源前缀：缓存条目与分页条目 id 相同时也不会撞 key
    fun postKeyAt(index: Int): String {
        val fromPaging = index < postPagingItems.itemCount
        val id = postAt(index)?.id ?: index
        return if (fromPaging) "$id" else "cached_$id"
    }

    LaunchedEffect(postPagingItems.itemSnapshotList.items) {
        onPostsLoaded(postPagingItems.itemSnapshotList.items)
    }
    var previewMedia by remember { mutableStateOf<List<PostMedia>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }

    previewMedia?.let { media ->
        PostMediaPreviewDialog(
            media = media,
            initialIndex = previewIndex,
            onDismiss = { previewMedia = null },
        )
    }

    val listContentPadding = PaddingValues(
        start = FoxSpacing.pagePadding,
        end = FoxSpacing.pagePadding,
        top = 8.dp,
        bottom = FoxSpacing.tabChromeBottom,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlazaTabHeader(
                layoutMode = layoutMode,
                onOpenDrawer = onOpenDrawer,
                onSearchClick = onSearchClick,
                onToggleLayout = { onIntent(CommunityPlazaIntent.ToggleLayoutMode) },
                onCreatePost = onCreatePost,
                onPublishQueue = onPublishQueue,
            )

            when {
                // 有缓存时优先展示旧数据（下面统一走列表分支），只有「无缓存 + 加载中」才转圈
                postPagingItems.loadState.refresh is LoadState.Loading &&
                    postPagingItems.itemCount == 0 && cachedPosts.isEmpty() -> {
                    LoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        useLottie = false,
                    )
                }
                postPagingItems.loadState.refresh is LoadState.Error &&
                    postPagingItems.itemCount == 0 && cachedPosts.isEmpty() -> {
                    ErrorView(
                        modifier = Modifier.fillMaxSize(),
                        message = (postPagingItems.loadState.refresh as LoadState.Error)
                            .error.message ?: "加载失败",
                        onRetry = { postPagingItems.retry() },
                    )
                }
                postPagingItems.loadState.refresh is LoadState.NotLoading &&
                    postPagingItems.itemCount == 0 && cachedPosts.isEmpty() -> {
                    ErrorView(
                        modifier = Modifier.fillMaxSize(),
                        message = "暂无动态，点击右上角发布第一条吧",
                        showIcon = false,
                    )
                }
                else -> {
                    PullToRefreshBox(
                        // 已有内容（缓存或已加载）时分页在后台刷新 → 顶部转圈提示「新数据在路上」
                        isRefreshing = state.isRefreshing ||
                            (postPagingItems.loadState.refresh is LoadState.Loading && postCount > 0),
                        onRefresh = { onIntent(CommunityPlazaIntent.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (layoutMode) {
                            PlazaLayoutMode.STAGGERED -> {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = listContentPadding,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalItemSpacing = 8.dp,
                                ) {
                                    items(
                                        count = postCount,
                                        key = { index -> postKeyAt(index) },
                                    ) { index ->
                                        val post = postAt(index) ?: return@items
                                        val displayPost = state.applyLikeOverride(post)
                                        PostNoteGridCard(
                                            post = displayPost,
                                            onClick = { onPostDetail(displayPost.id) },
                                            onLikeClick = {
                                                onIntent(
                                                    CommunityPlazaIntent.OnPostLikeClick(displayPost),
                                                )
                                            },
                                            onTagClick = onTagClick,
                                        )
                                    }
                                    if (postPagingItems.loadState.append is LoadState.Loading) {
                                        item(
                                            key = "loading_more",
                                            span = StaggeredGridItemSpan.FullLine,
                                        ) {
                                            LoadingIndicator(useLottie = false)
                                        }
                                    }
                                }
                            }
                            PlazaLayoutMode.LIST -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    // 条目自带 20dp 横向内边距（FoxEditorialPostItem），这里不能再叠
                                    // pagePadding，否则内容会离屏边 44dp；条目之间用发丝线分隔，
                                    // 所以纵向间隔取 0（旧写法 12dp 会让发丝线悬空成"卡片"）。
                                    contentPadding = PaddingValues(
                                        start = 0.dp,
                                        end = 0.dp,
                                        top = 8.dp,
                                        bottom = FoxSpacing.tabChromeBottom,
                                    ),
                                ) {
                                    items(
                                        count = postCount,
                                        key = { index -> postKeyAt(index) },
                                    ) { index ->
                                        val post = postAt(index) ?: return@items
                                        val displayPost = state.applyLikeOverride(post)
                                        val isOwn = displayPost.userId == currentUserId
                                        FoxEditorialPostItem(
                                            post = displayPost,
                                            previewComment = state.commentPreviews[post.id],
                                            isOwnPost = isOwn,
                                            onPostClick = { onPostDetail(displayPost.id) },
                                            onLikeClick = {
                                                onIntent(
                                                    CommunityPlazaIntent.OnPostLikeClick(displayPost),
                                                )
                                            },
                                            onCommentClick = {
                                                onIntent(
                                                    CommunityPlazaIntent.StartComment(displayPost.id),
                                                )
                                            },
                                            onShareClick = { onSharePost(displayPost) },
                                            onUserClick = { onPostDetail(displayPost.id) },
                                            onEditClick = { onEditPost(displayPost.id) },
                                            onDeleteClick = { onDeletePost(displayPost) },
                                            onReportClick = { onReportPost(displayPost) },
                                            onImageClick = { imageIndex ->
                                                previewMedia = displayPost.displayMedia
                                                previewIndex = imageIndex
                                            },
                                            onMusicPlayClick = {
                                                val music = displayPost.music
                                                    ?: return@FoxEditorialPostItem
                                                if (playerState.currentMusic?.id == music.id) {
                                                    onTogglePlay()
                                                } else {
                                                    onPlayMusic(displayPost)
                                                }
                                            },
                                            onMusicRowClick = {
                                                onPlayMusic(displayPost)
                                                onNavigateToPlayer()
                                            },
                                            isCurrentTrack = displayPost.music?.id ==
                                                playerState.currentMusic?.id,
                                            isPlaying = displayPost.music?.id ==
                                                playerState.currentMusic?.id &&
                                                playerState.isPlaying,
                                            onTagClick = onTagClick,
                                        )
                                    }
                                    if (postPagingItems.loadState.append is LoadState.Loading) {
                                        item(key = "loading_more") {
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

        if (layoutMode == PlazaLayoutMode.LIST && state.commentingPostId != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(bottom = 88.dp),
            ) {
                PostCommentInputBar(
                    value = state.commentInput,
                    onValueChange = {
                        onIntent(CommunityPlazaIntent.UpdateCommentInput(it))
                    },
                    onSend = { onIntent(CommunityPlazaIntent.SendComment) },
                    isSending = state.isCommentSending,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlazaTabHeader(
    layoutMode: PlazaLayoutMode,
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleLayout: () -> Unit,
    onCreatePost: () -> Unit,
    onPublishQueue: () -> Unit,
) {
    var showPostMenu by remember { mutableStateOf(false) }
    if (showPostMenu) {
        PlazaPostMenuBottomSheet(
            onDismiss = { showPostMenu = false },
            onCreatePost = onCreatePost,
            onPublishQueue = onPublishQueue,
        )
    }
    FoxPageHeader(
        title = "广场",
        onOpenDrawer = onOpenDrawer,
        actions = {
            FoxCircleIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "搜索帖子",
                onClick = onSearchClick,
            )
            FoxCircleIconButton(
                icon = when (layoutMode) {
                    PlazaLayoutMode.STAGGERED -> Icons.AutoMirrored.Filled.ViewList
                    PlazaLayoutMode.LIST -> Icons.Filled.GridView
                },
                contentDescription = when (layoutMode) {
                    PlazaLayoutMode.STAGGERED -> "切换为列表"
                    PlazaLayoutMode.LIST -> "切换为瀑布流"
                },
                onClick = onToggleLayout,
            )
            FoxCircleIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "发帖",
                onClick = { showPostMenu = true },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlazaPostMenuBottomSheet(
    onDismiss: () -> Unit,
    onCreatePost: () -> Unit,
    onPublishQueue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FoxSpacing.pagePadding, vertical = 8.dp),
        ) {
            PlazaPostMenuActionRow(
                icon = Icons.Default.EditNote,
                label = "发布动态",
                onClick = {
                    onDismiss()
                    onCreatePost()
                },
            )
            PlazaPostMenuActionRow(
                icon = Icons.Default.TaskAlt,
                label = "任务状态",
                onClick = {
                    onDismiss()
                    onPublishQueue()
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlazaPostMenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
