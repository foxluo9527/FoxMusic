package com.fox.music.feature.home

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.SectionHeader
import com.fox.music.core.ui.theme.CoverShape
import com.fox.music.core.ui.theme.FoxDisplayFont
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.SearchBarShape

const val HOME_ROUTE = "home"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: HomeViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit = { _, _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onPlaylistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlaylistCategoryClick: () -> Unit = {},
    onAlbumCategoryClick: () -> Unit = {},
    onRecommendClick: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val recommendedMusicPagingItems = state.recommendedMusic.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    effect.musicList,
                    HOME_ROUTE,
                    null,
                )
                is HomeEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlistId)
                is HomeEffect.NavigateToAlbum -> onAlbumClick(effect.albumId)
                is HomeEffect.NavigateToSearch -> onSearchClick()
                is HomeEffect.NavigateToPlaylistCategory -> onPlaylistCategoryClick()
                is HomeEffect.NavigateToAlbumCategory -> onAlbumCategoryClick()
            }
        }
    }

    LaunchedEffect(recommendedMusicPagingItems.itemSnapshotList.items) {
        val items = recommendedMusicPagingItems.itemSnapshotList.items
        viewModel.updateCurrentMusicList(items)
        if (items.isNotEmpty()) {
            updateMusicList(items, HOME_ROUTE)
        }
    }

    with(sharedTransitionScope) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendIntent(HomeIntent.Refresh) },
            modifier = modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = FoxSpacing.heroTop,
                    bottom = FoxSpacing.tabChromeBottom, // 悬浮 MiniPlayer + 底栏
                    start = FoxSpacing.pagePadding,
                    end = FoxSpacing.pagePadding,
                ),
                verticalArrangement = Arrangement.spacedBy(FoxSpacing.itemSpacing),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(
                    span = { GridItemSpan(4) },
                    key = "brand_hero",
                    contentType = "hero",
                ) {
                    HomeBrandHero(
                        onSearchClick = { viewModel.onSearchClick() },
                        hotHint = state.hotKeywords.firstOrNull()?.keyword,
                    )
                }

                if (state.recentPlays.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(4) },
                        key = "recent_header",
                        contentType = "section_header",
                    ) {
                        SectionHeader(title = "最近在听")
                    }
                    item(
                        span = { GridItemSpan(4) },
                        key = "recent_row",
                        contentType = "recent_row",
                    ) {
                        RecentPlaysRow(
                            recentPlays = state.recentPlays,
                            onMusicClick = { music -> viewModel.onMusicClick(music) },
                        )
                    }
                }

                if (state.recommendedPlaylists.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(4) },
                        key = "playlist_header",
                        contentType = "section_header",
                    ) {
                        SectionHeader(
                            title = "推荐歌单",
                            onMoreClick = { viewModel.onPlaylistMoreClick() },
                        )
                    }
                    items(
                        count = state.recommendedPlaylists.size,
                        key = { index -> "playlist_${state.recommendedPlaylists[index].id}" },
                        contentType = { "playlist_item" },
                    ) { index ->
                        val playlist = state.recommendedPlaylists[index]
                        PlaylistGridItem(
                            playlist = playlist,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = { viewModel.sendIntent(HomeIntent.OnPlaylistClick(playlist)) },
                        )
                    }
                }

                item(
                    span = { GridItemSpan(4) },
                    key = "music_header",
                    contentType = "section_header",
                ) {
                    SectionHeader(
                        title = "为你精选",
                        onMoreClick = onRecommendClick,
                    )
                }

                items(
                    count = minOf(recommendedMusicPagingItems.itemCount, 5),
                    key = { index -> "music_${recommendedMusicPagingItems[index]?.id ?: index}" },
                    span = { GridItemSpan(4) },
                    contentType = { "music_item" },
                ) { index ->
                    recommendedMusicPagingItems[index]?.let { music ->
                        MusicListItem(
                            music = music,
                            sharedTransitionScope = this@with,
                            animatedContentScope = animatedContentScope,
                            onClick = { viewModel.onMusicClick(music) },
                            onMoreClick = { onMusicMoreClick(music) },
                        )
                    }
                }

                if (state.recommendedAlbums.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(4) },
                        key = "album_header",
                        contentType = "section_header",
                    ) {
                        SectionHeader(
                            title = "热门专辑",
                            onMoreClick = { viewModel.onAlbumMoreClick() },
                        )
                    }
                    items(
                        count = state.recommendedAlbums.size,
                        key = { index -> "album_${state.recommendedAlbums[index].id}" },
                        contentType = { "album_item" },
                    ) { index ->
                        val album = state.recommendedAlbums[index]
                        AlbumGridItem(
                            album = album,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = { viewModel.sendIntent(HomeIntent.OnAlbumClick(album)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBrandHero(
    onSearchClick: () -> Unit,
    hotHint: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "FoxMusic",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FoxDisplayFont,
                fontWeight = FontWeight.Bold,
            ),
            color = scheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 2.dp,
                    shape = SearchBarShape,
                    ambientColor = scheme.onBackground.copy(alpha = 0.08f),
                    spotColor = scheme.onBackground.copy(alpha = 0.12f),
                )
                .clip(SearchBarShape)
                .background(scheme.surface)
                .border(
                    width = 1.dp,
                    color = scheme.outline.copy(alpha = 0.55f),
                    shape = SearchBarShape,
                )
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = scheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = hotHint?.let { "试试「$it」" } ?: "搜索歌曲、艺人、歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentPlaysRow(
    recentPlays: List<PlayHistory>,
    onMusicClick: (Music) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(recentPlays, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .width(108.dp)
                    .clickable { onMusicClick(item.music) },
            ) {
                CachedImage(
                    imageUrl = item.music.coverImage,
                    contentDescription = item.music.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = CoverShape,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.music.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.music.artists.joinToString(", ") { it.name }
                        .ifBlank { "未知艺人" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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
                .padding(horizontal = 2.dp)
                .clickable(onClick = onClick),
        ) {
            CachedImage(
                imageUrl = playlist.coverImage,
                contentDescription = playlist.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState("playlist-cover-${playlist.id}"),
                        animatedContentScope,
                    ),
                shape = CoverShape,
                placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
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
                .padding(horizontal = 2.dp)
                .clickable(onClick = onClick),
        ) {
            CachedImage(
                imageUrl = album.coverImage,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState("album-cover-${album.id}"),
                        animatedContentScope,
                    ),
                shape = CoverShape,
                placeholderIcon = Icons.Filled.Album,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
