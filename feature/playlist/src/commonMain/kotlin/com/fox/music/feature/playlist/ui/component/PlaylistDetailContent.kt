package com.fox.music.feature.playlist.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.theme.CoverShapeRail
import com.fox.music.core.ui.component.DeletePlaylistConfirmDialog
import com.fox.music.core.ui.component.DetailIntroContent
import com.fox.music.core.ui.component.DetailIntroData
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxLinearProgress
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.JumpToPlayingFabForList
import com.fox.music.core.ui.component.LocalJumpHighlight
import com.fox.music.core.ui.component.LocalNowPlayingMusicId
import com.fox.music.core.ui.component.MusicSelectionBottomBar
import com.fox.music.core.ui.component.PlaylistManageBottomSheet
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarIconAction
import com.fox.music.core.ui.component.FoxTopBarSpec
import com.fox.music.core.ui.component.FoxTopBarTextAction
import com.fox.music.feature.playlist.viewmodel.HeaderInfo
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailState
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailContent(
    state: PlaylistDetailState,
    pagingItems: LazyPagingItems<Music>,
    playlistKey: String,
    playlistId: Long,
    canManagePlaylist: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onArtistClick: (Long) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onBack: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onRefresh: () -> Unit,
    onAddSelectedToQueue: () -> Unit,
    onRemoveSelectedFromPlaylist: () -> Unit,
    onAddSelectedToPlaylist: () -> Unit,
    onDownloadSelected: () -> Unit,
    onPauseImport: () -> Unit = {},
    onResumeImport: () -> Unit = {},
    onRetryImport: () -> Unit = {},
    onCancelFailedImport: () -> Unit = {},
    onOpenImportDetail: () -> Unit = {},
    onEditPlaylist: (Long) -> Unit = {},
    onDeletePlaylist: () -> Unit = {},
    currentMusicId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val jumpScope = rememberCoroutineScope()
    // 显式传入优先，其次读全局「正在播放」（app 层在 NavHost 外层统一 provide）
    val nowPlayingId = currentMusicId ?: LocalNowPlayingMusicId.current
    // 头部占 2 个 item（封面头部 + 操作行），导入中再多一个横幅；歌曲项在其后。
    val importBannerVisible = state.importProgress?.status?.let {
        it == "queued" || it == "running" || it == "paused" || it == "needs_retry"
    } == true
    val musicStartIndex = if (importBannerVisible) 3 else 2
    val targetLazyIndex = remember(nowPlayingId, pagingItems.itemCount, musicStartIndex) {
        if (nowPlayingId == null) {
            -1
        } else {
            var pagingIndex = -1
            for (i in 0 until pagingItems.itemCount) {
                if (pagingItems.peek(i)?.id == nowPlayingId) {
                    pagingIndex = i
                    break
                }
            }
            if (pagingIndex >= 0) musicStartIndex + pagingIndex else -1
        }
    }
    var headerHeightPx by remember { mutableStateOf(0f) }
    val showToolbarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > headerHeightPx
        }
    }
    val showToolbarActions by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 2
        }
    }
    var showManageSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showIntro by rememberSaveable { mutableStateOf(false) }
    var previewCoverUrl by remember { mutableStateOf<String?>(null) }

    previewCoverUrl?.let { url ->
        PostImagePreviewDialog(
            images = listOf(url),
            initialIndex = 0,
            onDismiss = { previewCoverUrl = null },
        )
    }

    // 歌单 / 专辑 / 榜单 / 歌手：点头部（封面、名称、简介）进入介绍详情页，完整展示简介
    val canShowIntro = state.detailType == DetailType.PLAYLIST ||
            state.detailType == DetailType.ALBUM ||
            state.detailType == DetailType.RANK ||
            state.detailType == DetailType.ARTIST
    val introData = state.headerInfo?.takeIf { showIntro && canShowIntro }?.toDetailIntroData()
    if (introData != null) {
        BackHandler { showIntro = false }
        DetailIntroContent(
            data = introData,
            onBack = { showIntro = false },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        // 页面底色由外层（Android MainScreenWithBottomBar / iOS Compose 宿主的
        // foxTabPageBackground）统一提供，这里必须透明，否则实心 background
        // 会盖掉主题色顶光，详情页看起来是死板的纸色。
        containerColor = Color.Transparent,
        topBar = {
            if (state.isSelectionMode) {
                FoxTopBar(
                    title = "已选 ${state.selectedMusicIds.size} 首",
                    showBackButton = true,
                    onBackClick = onExitSelectionMode,
                    actions = {
                        FoxTopBarTextAction(
                            text = "全选",
                            onClick = onSelectAll,
                        )
                    },
                )
            } else {
                FoxTopBar(
                    showBackButton = true,
                    onBackClick = onBack,
                    titleContent = {
                        if (state.headerInfo != null && showToolbarTitle) {
                            Row(
                                Modifier
                                    .fillMaxHeight()
                                    .then(
                                        if (canShowIntro) {
                                            Modifier.clickable { showIntro = true }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                state.headerInfo?.coverImage?.let { url ->
                                    CachedImage(
                                        imageUrl = url,
                                        contentDescription = state.headerInfo?.title,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    if (canShowIntro) showIntro = true
                                                },
                                                onLongClick = { previewCoverUrl = url },
                                            ),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    text = state.headerInfo?.title
                                        ?: getTypeTitle(state.detailType),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.headerInfo != null && showToolbarActions) {
                            val headerInfo = state.headerInfo!!
                            val trackCount =
                                headerInfo.trackCount.takeIf { it > 0 } ?: pagingItems.itemCount
                            FoxTopBarIconAction(
                                icon = Icons.Default.PlayArrow,
                                contentDescription = "播放全部($trackCount)",
                                onClick = onPlayAll,
                            )
                            val showFavorite = state.detailType != DetailType.RECOMMEND &&
                                    state.detailType != DetailType.NEW_MUSIC &&
                                    state.detailType != DetailType.FAVORITE_MUSIC &&
                                    state.detailType != DetailType.PLAY_HISTORY &&
                                    state.detailType != DetailType.ARTIST
                            if (showFavorite) {
                                if (state.isFavoriteLoading) {
                                    Box(
                                        modifier = Modifier.size(FoxTopBarSpec.ActionBox),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    FoxTopBarIconAction(
                                        icon = if (headerInfo.isFavorite) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Filled.FavoriteBorder
                                        },
                                        contentDescription = if (headerInfo.isFavorite) "已收藏" else "收藏",
                                        onClick = onToggleFavorite,
                                        tint = if (headerInfo.isFavorite) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        } else if (canManagePlaylist) {
                            FoxTopBarIconAction(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                onClick = { showManageSheet = true },
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode && state.selectedMusicIds.isNotEmpty()) {
                MusicSelectionBottomBar(
                    selectedCount = state.selectedMusicIds.size,
                    showRemoveFromPlaylist = canManagePlaylist,
                    onAddToQueue = onAddSelectedToQueue,
                    onRemoveFromPlaylist = onRemoveSelectedFromPlaylist,
                    onAddToPlaylist = onAddSelectedToPlaylist,
                    onDownload = onDownloadSelected,
                )
            }
        },
        floatingActionButton = {
            val jumpHighlight = LocalJumpHighlight.current
            JumpToPlayingFabForList(
                listState = listState,
                targetIndex = targetLazyIndex,
                onClick = {
                    jumpScope.launch {
                        listState.animateScrollToItem(targetLazyIndex)
                        // 到位后再闪一下，强调「就是这一行」
                        jumpHighlight.flash(nowPlayingId)
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.headerInfo == null -> {
                LoadingIndicator(useLottie = false)
            }

            state.error != null && state.headerInfo == null -> {
                ErrorView(message = state.error!!)
            }

            else -> {
                var isUserRefresh by remember { mutableStateOf(false) }
                val isPagingRefreshing = pagingItems.loadState.refresh is LoadState.Loading &&
                        isUserRefresh
                PullToRefreshBox(
                    isRefreshing = isPagingRefreshing,
                    onRefresh = {
                        isUserRefresh = true
                        onRefresh()
                        pagingItems.refresh()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    CollectionDetailContent(
                        state = state,
                        pagingItems = pagingItems,
                        listState = listState,
                        showToolbarTitle = showToolbarTitle,
                        showToolbarActions = showToolbarActions,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMusicClick = onMusicClick,
                        onPlayAll = onPlayAll,
                        onToggleFavorite = onToggleFavorite,
                        onMusicMoreClick = onMusicMoreClick,
                        onArtistClick = onArtistClick,
                        onEnterSelectionMode = onEnterSelectionMode,
                        onToggleSelection = onToggleSelection,
                        onHeaderClick = { showIntro = true },
                        onCoverLongClick = { previewCoverUrl = it },
                        onPauseImport = onPauseImport,
                        onResumeImport = onResumeImport,
                        onRetryImport = onRetryImport,
                        onCancelFailedImport = onCancelFailedImport,
                        onOpenImportDetail = onOpenImportDetail,
                        suppressRefreshIndicator = isPagingRefreshing,
                        onMeasureHeight = { headerHeightPx = it },
                        nowPlayingId = nowPlayingId,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    val playlistTitle = state.headerInfo?.title.orEmpty()
    if (showManageSheet && playlistTitle.isNotEmpty()) {
        PlaylistManageBottomSheet(
            playlistTitle = playlistTitle,
            onDismiss = { showManageSheet = false },
            onEdit = {
                showManageSheet = false
                onEditPlaylist(playlistId)
            },
            onDelete = {
                showManageSheet = false
                showDeleteConfirm = true
            },
            onPlayAll = {
                onPlayAll()
                showManageSheet = false
            },
        )
    }

    if (showDeleteConfirm && playlistTitle.isNotEmpty()) {
        DeletePlaylistConfirmDialog(
            playlistTitle = playlistTitle,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                onDeletePlaylist()
                showDeleteConfirm = false
            },
        )
    }
}

@Composable
private fun CollectionDetailContent(
    state: PlaylistDetailState,
    pagingItems: LazyPagingItems<Music>,
    listState: LazyListState,
    showToolbarTitle: Boolean = false,
    showToolbarActions: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onArtistClick: (Long) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onHeaderClick: () -> Unit = {},
    onCoverLongClick: (String) -> Unit = {},
    onPauseImport: () -> Unit = {},
    onResumeImport: () -> Unit = {},
    onRetryImport: () -> Unit = {},
    onCancelFailedImport: () -> Unit = {},
    onOpenImportDetail: () -> Unit = {},
    suppressRefreshIndicator: Boolean = false,
    onMeasureHeight: (Float) -> Unit,
    nowPlayingId: Long? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        // 预留给底部悬浮 MiniPlayer + 手势条，避免最后几首无法滑出遮挡
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 头部信息区域
        item {
            state.headerInfo?.let { info ->
                CollectionDetailHeader(
                    headerInfo = info,
                    onClick = if (!state.isSelectionMode) onHeaderClick else null,
                    onCoverLongClick = onCoverLongClick,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        onMeasureHeight(coordinates.size.height.toFloat())
                    }
                )
            }
        }

        item {
            val actionAlpha = if (showToolbarActions) 0f else 1f
            state.headerInfo?.let { headerInfo ->
                ActionButtonRow(
                    showFavorite = state.detailType !== DetailType.RECOMMEND &&
                            state.detailType != DetailType.NEW_MUSIC &&
                            state.detailType != DetailType.FAVORITE_MUSIC &&
                            state.detailType != DetailType.PLAY_HISTORY &&
                            state.detailType != DetailType.ARTIST,
                    trackCount = headerInfo.trackCount.takeIf { it > 0 } ?: pagingItems.itemCount,
                    isFavorite = headerInfo.isFavorite,
                    isFavoriteLoading = state.isFavoriteLoading,
                    onPlayAll = onPlayAll,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .graphicsLayer { alpha = actionAlpha }
                        .animateContentSize()
                )
            }
        }

        state.importProgress?.takeIf {
            it.status in listOf("queued", "running", "paused", "needs_retry")
        }?.let { progress ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // 点按横幅进入「导入详情」：逐首查看上传状态、失败的可手动选音源
                            .clickable(onClick = onOpenImportDetail)
                            .padding(vertical = 2.dp),
                    ) {
                        val processed =
                            (progress.done + progress.failed).coerceAtMost(progress.total)
                        val fraction = if (progress.total > 0) {
                            val inFlight = if (!progress.currentTitle.isNullOrBlank() &&
                                progress.status == "running"
                            ) {
                                0.5f
                            } else {
                                0f
                            }
                            ((processed.toFloat() + inFlight) / progress.total.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val titleText = when (progress.status) {
                            "paused" -> "导入已暂停 $processed/${progress.total}"
                            "needs_retry" -> "剩余 ${progress.failed} 首导入失败"
                            else -> "正在导入 $processed/${progress.total}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "查看导入详情",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(18.dp),
                            )
                        }
                        if (progress.status == "needs_retry") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "取消",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable(onClick = onCancelFailedImport),
                                )
                                Text(
                                    text = "　查看详情",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        } else {
                            progress.currentTitle?.takeIf { it.isNotBlank() }?.let { title ->
                                Text(
                                    text = "当前：$title",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FoxLinearProgress(
                                progress = fraction,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    when (progress.status) {
                        "needs_retry" -> IconButton(onClick = onRetryImport) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试失败歌曲")
                        }

                        "paused" -> IconButton(onClick = onResumeImport) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "继续导入")
                        }

                        else -> IconButton(onClick = onPauseImport) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停导入")
                        }
                    }
                }
            }
        }

        // 歌曲列表
        items(
            count = pagingItems.itemCount,
            key = { index ->
                val musicId = pagingItems.peek(index)?.id
                musicId?.let { "music_$it" } ?: "placeholder_$index"
            }
        ) { index ->
            val music = pagingItems[index]
            if (music == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = null,
                        contentDescription = "加载中",
                        modifier = Modifier.size(56.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "加载中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                MusicListItem(
                    music = music,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    modifier = Modifier.padding(horizontal = if (!state.isSelectionMode) 16.dp else 8.dp),
                    onClick = { onMusicClick(music) },
                    onMoreClick = { onMusicMoreClick(music) },
                    isSelectionMode = state.isSelectionMode,
                    isSelected = music.id in state.selectedMusicIds,
                    onLongClick = { onEnterSelectionMode(music.id) },
                    onSelectionToggle = { onToggleSelection(music.id) },
                    isNowPlaying = music.id == nowPlayingId,
                )
            }
        }

        // 加载状态处理（下拉刷新时由 PullToRefreshBox 展示指示器）
        when {
            pagingItems.loadState.refresh is LoadState.Loading &&
                    pagingItems.itemCount == 0 &&
                    !suppressRefreshIndicator -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            pagingItems.loadState.append is LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            pagingItems.loadState.refresh is LoadState.Error -> {
                item {
                    val error = (pagingItems.loadState.refresh as LoadState.Error).error
                    ErrorView(
                        message = error.message ?: "加载失败",
                        onRetry = { pagingItems.retry() }
                    )
                }
            }

            pagingItems.loadState.append is LoadState.Error -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载更多失败，点击重试",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { pagingItems.retry() }
                        )
                    }
                }
            }

            pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 头部信息区域组件
 */
@Composable
private fun CollectionDetailHeader(
    headerInfo: HeaderInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onCoverLongClick: (String) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 不铺 surface：歌单详情的页面底色是 background（纸色），
            // 头部再垫一层 surface 会在封面块上下切出两条白边，像拼贴上去的。
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (headerInfo.coverImage != null) {// 封面图
            CachedImage(
                imageUrl = headerInfo.coverImage,
                contentDescription = headerInfo.title,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CoverShapeRail)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = { onCoverLongClick(headerInfo.coverImage) },
                    ),
                shape = CoverShapeRail
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else if (headerInfo.detailType == DetailType.FAVORITE_MUSIC ||
            headerInfo.detailType == DetailType.PLAY_HISTORY
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CoverShapeRail,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (headerInfo.detailType == DetailType.FAVORITE_MUSIC) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = headerInfo.title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = headerInfo.title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 类型标签 + 创建时间：白底描边胶囊（不再用 primaryContainer 色块）
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                    ),
                ) {
                    Text(
                        text = getTypeLabel(headerInfo.detailType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (headerInfo.detailType == DetailType.PLAYLIST){
                    headerInfo.createdAt?.let { date ->
                        Text(
                            text = " · ${formatDate(date)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                headerInfo.releaseDate?.let { date ->
                    Text(
                        text = " · ${formatDate(date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 标题：长歌单名允许两行（一行截断丢信息最伤）
            Text(
                text = headerInfo.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 曲目数 / 创建者
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                headerInfo.trackCount.takeIf { it > 0 }?.let {
                    Text(
                        text = "${headerInfo.trackCount}首",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                headerInfo.creatorName?.let { creator ->
                    Text(
                        text = " · 创建者: $creator",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                headerInfo.artists?.let { artists ->
                    Text(
                        text = " · $artists",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 描述
            headerInfo.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 操作按钮行组件
 */
@Composable
private fun ActionButtonRow(
    isFavorite: Boolean,
    showFavorite: Boolean = true,
    isFavoriteLoading: Boolean = false,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    trackCount: Int?,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        ActionButton(
            icon = Icons.Default.PlayArrow,
            label = "播放全部($trackCount)",
            onClick = onPlayAll,
            emphasized = true,
        )
        if (showFavorite) {
            Spacer(Modifier.width(10.dp))
            ActionButton(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isFavoriteLoading) "处理中..." else if (isFavorite) "已收藏" else "收藏",
                onClick = onToggleFavorite,
                enabled = !isFavoriteLoading,
            )
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .height(44.dp)
            .then(
                if (emphasized) {
                    // 「播放全部」是本页唯一实心块：视觉权重 = 功能权重
                    Modifier.background(scheme.onSurface, CircleShape)
                } else {
                    Modifier
                        .background(scheme.surface, CircleShape)
                        .border(1.dp, scheme.onSurface.copy(alpha = 0.10f), CircleShape)
                }
            )
            .weight(1f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (emphasized) scheme.background else scheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (emphasized) scheme.background else scheme.onSurface
        )
    }
}

/** 把列表头部信息映射成「介绍」详情页的展示数据。 */
private fun HeaderInfo.toDetailIntroData(): DetailIntroData {
    val typeLabel = when (detailType) {
        DetailType.PLAYLIST -> "歌单"
        DetailType.ALBUM -> "专辑"
        DetailType.RANK -> "榜单"
        DetailType.RECOMMEND -> "推荐"
        DetailType.NEW_MUSIC -> "新歌"
        DetailType.FAVORITE_MUSIC -> "收藏"
        DetailType.PLAY_HISTORY -> "历史"
        DetailType.ARTIST -> "歌手"
    }
    val isAlbum = detailType == DetailType.ALBUM
    val subtitle = if (isAlbum) {
        artists?.takeIf { it.isNotBlank() }?.let { "歌手：$it" }
    } else {
        creatorName?.takeIf { it.isNotBlank() }?.let { "创建者：$it" }
    }
    val infoRows = buildList {
        if (isAlbum) {
            releaseDate?.takeIf { it.isNotBlank() }?.let { add("发行时间" to formatDate(it)) }
            publisher?.takeIf { it.isNotBlank() }?.let { add("发行公司" to it) }
            language?.takeIf { it.isNotBlank() }?.let { add("语言" to it) }
        } else {
            createdAt?.takeIf { it.isNotBlank() }?.let { add("创建时间" to formatDate(it)) }
        }
        if (trackCount > 0) add("曲目数" to "$trackCount 首")
    }
    return DetailIntroData(
        title = title,
        coverUrl = coverImage,
        typeLabel = typeLabel,
        subtitle = subtitle,
        tags = tags,
        description = description,
        infoRows = infoRows,
    )
}

private fun getTypeTitle(type: DetailType): String {
    return when (type) {
        DetailType.PLAYLIST -> "歌单详情"
        DetailType.ALBUM -> "专辑详情"
        DetailType.RANK -> "排行榜"
        DetailType.RECOMMEND -> "推荐歌曲"
        DetailType.NEW_MUSIC -> "新歌速递"
        DetailType.FAVORITE_MUSIC -> "我的收藏"
        DetailType.PLAY_HISTORY -> "历史播放"
        DetailType.ARTIST -> "歌手全部歌曲"
    }
}

private fun getTypeLabel(type: DetailType): String {
    return when (type) {
        DetailType.PLAYLIST -> "歌单"
        DetailType.ALBUM -> "专辑"
        DetailType.RANK -> "榜单"
        DetailType.RECOMMEND -> "推荐"
        DetailType.NEW_MUSIC -> "新歌"
        DetailType.FAVORITE_MUSIC -> "收藏"
        DetailType.PLAY_HISTORY -> "历史"
        DetailType.ARTIST -> "歌手"
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        dateStr.take(10) // 取 "YYYY-MM-DD" 部分
    } catch (e: Exception) {
        dateStr
    }
}
