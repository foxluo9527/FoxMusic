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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.playlist.viewmodel.PlaylistListIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistListState

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
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val playlistPagingItems = state.playlists.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = {
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
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "返回"
                    )
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
            scrollBehavior = scrollBehavior
        )

        // Content
        when {
            playlistPagingItems.loadState.refresh == LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            playlistPagingItems.loadState.refresh is LoadState.Error -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = (playlistPagingItems.loadState.refresh as LoadState.Error).error.message
                        ?: "加载失败",
                    onRetry = { onIntent(PlaylistListIntent.Load) }
                )
            }

            playlistPagingItems.loadState.refresh is LoadState.NotLoading && playlistPagingItems.itemCount == 0 -> {
                ErrorView(
                    showIcon = false,
                    modifier = Modifier.fillMaxSize(),
                    message = "暂无歌单"
                )
            }

            else -> {
                with(sharedTransitionScope) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = playlistPagingItems.itemCount,
                            key = { index -> playlistPagingItems[index]?.id ?: index },
                            contentType = { "playlist_item" }
                        ) { index ->
                            playlistPagingItems[index]?.let { playlist ->
                                PlaylistGridItem(
                                    playlist = playlist,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    onClick = { onPlaylistClick(playlist) },
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
        Column(
            modifier = modifier
                .padding(horizontal = 4.dp)
                .clickable(onClick = onClick),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.medium,
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
                    shape = MaterialTheme.shapes.medium,
                    placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
