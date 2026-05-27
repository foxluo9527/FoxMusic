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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Artist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator

const val HOT_ARTIST_LIST_ROUTE = "hot_artist_list"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotArtistListScreen(
    modifier: Modifier = Modifier,
    viewModel: HotArtistListViewModel = hiltViewModel(),
    onArtistClick: (Long) -> Unit = {},
    onAllArtistsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val artistPagingItems = state.artists.collectAsLazyPagingItems()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(HotArtistListIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is HotArtistListEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                HotArtistListEffect.NavigateToAllArtists -> onAllArtistsClick()
                HotArtistListEffect.NavigateBack -> onBackClick()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("热门歌手") },
            navigationIcon = {
                IconButton(onClick = { viewModel.onBackClick() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "返回",
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )

        when {
            artistPagingItems.loadState.refresh == LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            artistPagingItems.loadState.refresh is LoadState.Error -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = (artistPagingItems.loadState.refresh as LoadState.Error).error.message
                        ?: "加载失败",
                    onRetry = { artistPagingItems.retry() },
                )
            }

            artistPagingItems.loadState.refresh is LoadState.NotLoading && artistPagingItems.itemCount == 0 -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = "暂无热门歌手",
                    showIcon = false,
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(
                        span = { GridItemSpan(4) },
                        key = "all_artists_entry",
                        contentType = "all_artists_entry",
                    ) {
                        AllArtistsEntry(
                            onClick = { viewModel.onAllArtistsClick() },
                        )
                    }

                    items(
                        count = artistPagingItems.itemCount,
                        key = { index ->
                            artistPagingItems.peek(index)?.let { artist ->
                                "hot_artist_${artist.id}_$index"
                            } ?: "hot_artist_loading_$index"
                        },
                        contentType = { "hot_artist_item" },
                    ) { index ->
                        artistPagingItems[index]?.let { artist ->
                            HotArtistGridItem(
                                artist = artist,
                                onClick = { viewModel.onArtistClick(artist.id) },
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
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            CachedImage(
                imageUrl = artist.avatar,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
