package com.fox.music.feature.playlist.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.theme.CoverShapeRail
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.feature.playlist.viewmodel.PlaylistListIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistListState
import kotlinx.coroutines.flow.distinctUntilChanged

const val PLAYLIST_LIST_ROUTE = "playlist_list"

fun playlistDetailRoute(playlistId: Long, type: DetailType = DetailType.PLAYLIST) =
    "playlist_detail/$playlistId/${type.name}"

/** 歌单广场页纯界面：state + onIntent 驱动，外壳负责 hiltViewModel() 与 effect 收集。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListContent(
    state: PlaylistListState,
    onIntent: (PlaylistListIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onPlaylistClick: (Playlist) -> Unit = {},
    onBackClick: () -> Unit = {},
    // 进详情页会离开组合，滚动位置靠外部（ViewModel）存档才能「原样返回」
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    val playlistPagingItems = state.playlists.collectAsLazyPagingItems()
    val refresh = playlistPagingItems.loadState.refresh
    // 分页第一页还没回来（或失败）时，用磁盘快照把格子铺上，避免空等。
    val showCached = playlistPagingItems.itemCount == 0 &&
        state.cachedPlaylists.isNotEmpty() &&
        refresh !is LoadState.NotLoading
    val visibleCount = if (showCached) state.cachedPlaylists.size else playlistPagingItems.itemCount
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
    )

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onScrollPositionChange(index, offset) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar
        FoxTopBar(
            showBackButton = true,
            onBackClick = onBackClick,
            titleContent = {
                // Tab Row
                if (state.categories.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = state.selectedCategoryIndex,
                        edgePadding = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                        divider = {}
                    ) {
                        // Fixed first tab: 推荐歌单
                        Tab(
                            selected = state.selectedCategoryIndex == 0,
                            onClick = { onIntent(PlaylistListIntent.SelectCategory(0)) },
                            text = { Text("推荐歌单") }
                        )

                        // Dynamic category tabs
                        state.categories.forEachIndexed { index, category ->
                            Tab(
                                selected = state.selectedCategoryIndex == index + 1,
                                onClick = {
                                    onIntent(
                                        PlaylistListIntent.SelectCategory(
                                            index + 1
                                        )
                                    )
                                },
                                text = { Text(category.name) }
                            )
                        }
                    }
                }
            },
        )

        // Content
        when {
            visibleCount == 0 && refresh is LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            visibleCount == 0 && refresh is LoadState.Error -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = (refresh as LoadState.Error).error.message
                        ?: "加载失败",
                    onRetry = { onIntent(PlaylistListIntent.Load) }
                )
            }

            visibleCount == 0 -> {
                ErrorView(
                    showIcon = false,
                    modifier = Modifier.fillMaxSize(),
                    message = "暂无歌单"
                )
            }

            else -> {
                with(sharedTransitionScope) {
                    LazyVerticalGrid(
                        // 3 列大封面：4 列时标题只剩碎字，与首页 rail 的封面尺度也脱节
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            vertical = 16.dp,
                            horizontal = FoxSpacing.pagePadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(
                            count = visibleCount,
                            key = { index ->
                                if (showCached) state.cachedPlaylists[index].id else playlistPagingItems[index]?.id ?: index
                            },
                            contentType = { "playlist_item" }
                        ) { index ->
                            val playlist = if (showCached) {
                                state.cachedPlaylists[index]
                            } else {
                                playlistPagingItems[index]
                            }
                            playlist?.let { playlist ->
                                PlaylistGridItem(
                                    playlist = playlist,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    onClick = { onPlaylistClick(playlist) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 歌单网格项组件 (复用自 HomeScreen)
@Composable
private fun PlaylistGridItem(
    playlist: Playlist,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = modifier.clickable(onClick = onClick),
        ) {
            CachedImage(
                imageUrl = playlist.coverImage,
                contentDescription = playlist.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState("playlist-cover-${playlist.id}"),
                        animatedContentScope
                    ),
                shape = CoverShapeRail,
                placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
            )
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = scheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
