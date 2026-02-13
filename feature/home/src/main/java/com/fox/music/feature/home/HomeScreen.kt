package com.fox.music.feature.home

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.Album
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.HotKeywordsCarousel
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.SectionHeader

const val HOME_ROUTE = "home"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: HomeViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String) -> Unit = {_, _, _ ->},
    updateMusicList: (List<Music>, String) -> Unit = {_, _ ->},
    onPlaylistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onPostClick: (Long) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlaylistCategoryClick: () -> Unit = {},
    onAlbumCategoryClick: () -> Unit = {},
    onSocialClick: () -> Unit = {},
    onRecommendClick:()-> Unit={}
) {
    val state by viewModel.uiState.collectAsState()
    val recommendedMusicPagingItems = state.recommendedMusic.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    recommendedMusicPagingItems.itemSnapshotList.items,
                    HOME_ROUTE
                )

                is HomeEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlistId)
                is HomeEffect.NavigateToAlbum -> onAlbumClick(effect.albumId)
                is HomeEffect.NavigateToPost -> onPostClick(effect.postId)
                is HomeEffect.NavigateToSearch -> onSearchClick()
                is HomeEffect.NavigateToPlaylistCategory -> onPlaylistCategoryClick()
                is HomeEffect.NavigateToAlbumCategory -> onAlbumCategoryClick()
                is HomeEffect.NavigateToSocial -> onSocialClick()
            }
        }
    }

    LaunchedEffect(recommendedMusicPagingItems.itemSnapshotList.items) {
        updateMusicList(recommendedMusicPagingItems.itemSnapshotList.items, HOME_ROUTE)
    }

    with(sharedTransitionScope) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {viewModel.sendIntent(HomeIntent.Refresh)},
            modifier = modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 搜索热词轮播 - 占据整行
                item(
                    span = {GridItemSpan(4)},
                    key = "hot_keywords",
                    contentType = "search_bar"
                ) {
                    HotKeywordsCarousel(
                        keywords = state.hotKeywords,
                        onSearchClick = {viewModel.onSearchClick()},
                    )
                }

                // 推荐歌单
                if (state.recommendedPlaylists.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = {GridItemSpan(4)},
                        key = "playlist_header",
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            title = "推荐歌单",
                            onMoreClick = {viewModel.onPlaylistMoreClick()},
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // 歌单网格项 - 每个占1列
                    items(
                        count = state.recommendedPlaylists.size,
                        key = {index -> "playlist_${state.recommendedPlaylists[index].id}"},
                        contentType = {"playlist_item"}
                    ) {index ->
                        val playlist = state.recommendedPlaylists[index]
                        PlaylistGridItem(
                            playlist = playlist,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = {viewModel.sendIntent(HomeIntent.OnPlaylistClick(playlist))},
                        )
                    }
                }

                // 推荐歌曲
                // 标题 - 占据整行
                item(
                    span = {GridItemSpan(4)},
                    key = "music_header",
                    contentType = "section_header"
                ) {
                    SectionHeader(
                        title = "推荐歌曲",
                        onMoreClick = onRecommendClick,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                // 歌曲列表 - 每个占据整行
                items(
                    count = minOf(recommendedMusicPagingItems.itemCount, 5),
                    key = {index -> "music_${recommendedMusicPagingItems[index]?.id ?: index}"},
                    span = {GridItemSpan(4)},
                    contentType = {"music_item"}
                ) {index ->
                    recommendedMusicPagingItems[index]?.let {music ->
                        MusicListItem(
                            music = music,
                            sharedTransitionScope = this@with,
                            animatedContentScope = animatedContentScope,
                            onClick = {viewModel.onMusicClick(music)},
                        )
                    }
                }

                // 推荐专辑
                if (state.recommendedAlbums.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = {GridItemSpan(4)},
                        key = "album_header",
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            title = "推荐专辑",
                            onMoreClick = {viewModel.onAlbumMoreClick()},
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // 专辑网格项 - 每个占1列
                    items(
                        count = state.recommendedAlbums.size,
                        key = {index -> "album_${state.recommendedAlbums[index].id}"},
                        contentType = {"album_item"}
                    ) {index ->
                        val album = state.recommendedAlbums[index]
                        AlbumGridItem(
                            album = album,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = {viewModel.sendIntent(HomeIntent.OnAlbumClick(album))},
                        )
                    }
                }

                // 社区动态
                if (state.posts.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = {GridItemSpan(4)},
                        key = "post_header",
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            title = "社区动态",
                            onMoreClick = {viewModel.onSocialMoreClick()},
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // 动态卡片 - 每个占据整行
                    items(
                        count = state.posts.size,
                        key = {index -> "post_${state.posts[index].id}"},
                        span = {GridItemSpan(4)},
                        contentType = {"post_item"}
                    ) {index ->
                        val post = state.posts[index]
                        PostCard(
                            post = post,
                            onPostClick = {viewModel.sendIntent(HomeIntent.OnPostClick(post))},
                            onLikeClick = {viewModel.sendIntent(HomeIntent.OnPostLike(post))},
                            onCommentClick = {viewModel.sendIntent(HomeIntent.OnPostClick(post))},
                            onUserClick = {onUserClick(post.userId)},
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

// 歌单网格项组件
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
                textAlign = TextAlign.Center
            )
        }
    }
}

// 专辑网格项组件
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
