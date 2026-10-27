package com.fox.music.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Artist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 热门歌手页纯界面。
 *
 * 批次 13 时这块还钉在安卓侧（androidx.paging 当时只有 androidMain 变体），
 * 批次 14 把 paging-common / paging-compose 拉进 commonMain 后即可跨端。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotArtistListContent(
    state: HotArtistListState,
    onIntent: (HotArtistListIntent) -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: (Long) -> Unit = {},
    onAllArtistsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    // 进详情页会离开组合，滚动位置靠外部（ViewModel）存档才能「原样返回」
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    val artistPagingItems = state.artists.collectAsLazyPagingItems()
    val refresh = artistPagingItems.loadState.refresh
    val showCached = artistPagingItems.itemCount == 0 &&
        state.cachedArtists.isNotEmpty() &&
        refresh !is LoadState.NotLoading
    val visibleCount = if (showCached) state.cachedArtists.size else artistPagingItems.itemCount
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
            title = "热门歌手",
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
                    onRetry = { artistPagingItems.retry() },
                )
            }

            visibleCount == 0 -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = "暂无热门歌手",
                    showIcon = false,
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        vertical = 12.dp,
                        horizontal = FoxSpacing.pagePadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(
                        span = { GridItemSpan(4) },
                        key = "all_artists_entry",
                        contentType = "all_artists_entry",
                    ) {
                            AllArtistsEntry(
                                onClick = onAllArtistsClick,
                                modifier = Modifier.animateItem(),
                            )
                    }

                    items(
                        count = visibleCount,
                        key = { index ->
                            val artist = if (showCached) {
                                state.cachedArtists[index]
                            } else {
                                artistPagingItems.peek(index)
                            }
                            artist?.let { "hot_artist_${it.id}_$index" } ?: "hot_artist_loading_$index"
                        },
                        contentType = { "hot_artist_item" },
                    ) { index ->
                        val artist = if (showCached) {
                            state.cachedArtists[index]
                        } else {
                            artistPagingItems[index]
                        }
                        artist?.let { artist ->
                            HotArtistGridItem(
                                artist = artist,
                                onClick = { onArtistClick(artist.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllArtistsEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "全部歌手",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看全部歌手",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun HotArtistGridItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = CircleShape,
            color = scheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
            tonalElevation = 0.dp,
        ) {
            CachedImage(
                imageUrl = artist.avatar,
                errorImageUrl = artist.coverImage,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 15.sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
