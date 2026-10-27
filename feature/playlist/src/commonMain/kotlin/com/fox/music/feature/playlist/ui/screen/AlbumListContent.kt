package com.fox.music.feature.playlist.ui.screen

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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.feature.playlist.viewmodel.AlbumListIntent
import com.fox.music.feature.playlist.viewmodel.AlbumListState
import kotlinx.coroutines.flow.distinctUntilChanged

const val ALBUM_LIST_ROUTE = "album_list"

/** 推荐专辑页纯界面：state + onIntent 驱动，外壳负责 hiltViewModel() 与 effect 收集。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListContent(
    state: AlbumListState,
    onIntent: (AlbumListIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
    onBackClick: () -> Unit = {},
    // 进详情页会离开组合，滚动位置靠外部（ViewModel）存档才能「原样返回」
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    val albumPagingItems = state.albums.collectAsLazyPagingItems()
    val refresh = albumPagingItems.loadState.refresh
    val showCached = albumPagingItems.itemCount == 0 &&
        state.cachedAlbums.isNotEmpty() &&
        refresh !is LoadState.NotLoading
    val visibleCount = if (showCached) state.cachedAlbums.size else albumPagingItems.itemCount
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
        FoxTopBar(
            title = "推荐专辑",
            showBackButton = true,
            onBackClick = onBackClick,
        )

        when {
            visibleCount == 0 && refresh is LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            visibleCount == 0 && refresh is LoadState.Error -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = (refresh as LoadState.Error).error.message
                        ?: "加载失败",
                    onRetry = { onIntent(AlbumListIntent.Load) }
                )
            }

            visibleCount == 0 -> {
                ErrorView(
                    showIcon = false,
                    modifier = Modifier.fillMaxSize(),
                    message = "暂无专辑"
                )
            }

            else -> {
                with(sharedTransitionScope) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = visibleCount,
                            key = { index ->
                                if (showCached) state.cachedAlbums[index].id else albumPagingItems[index]?.id ?: index
                            },
                            contentType = { "album_item" }
                        ) { index ->
                            val album = if (showCached) {
                                state.cachedAlbums[index]
                            } else {
                                albumPagingItems[index]
                            }
                            album?.let { album ->
                            AlbumGridItem(
                                album = album,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope,
                                onClick = { onAlbumClick(album) },
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

@Composable
private fun AlbumGridItem(
    album: Album,
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                CachedImage(
                    imageUrl = album.coverImage,
                    contentDescription = album.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedElement(
                            sharedTransitionScope.rememberSharedContentState("album-cover-${album.id}"),
                            animatedContentScope
                        ),
                    shape = MaterialTheme.shapes.medium,
                    placeholderIcon = Icons.Filled.Album,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
