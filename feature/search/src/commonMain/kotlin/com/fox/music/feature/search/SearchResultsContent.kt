package com.fox.music.feature.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem

@Composable
internal fun MusicSearchResults(
    pagingItems: LazyPagingItems<Music>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onArtistClick: (Long) -> Unit,
    onMusicListChanged: (List<Music>) -> Unit,
    updateMusicList: (List<Music>, String) -> Unit,
    playlistKey: String,
    hasSearched: Boolean,
) {
    LaunchedEffect(pagingItems.itemSnapshotList.items, hasSearched, playlistKey) {
        if (hasSearched) {
            val items = pagingItems.itemSnapshotList.items
            onMusicListChanged(items)
            if (items.isNotEmpty()) {
                updateMusicList(items, playlistKey)
            }
        }
    }

    PagingResultList(
        pagingItems = pagingItems,
        emptyMessage = "未找到相关单曲",
    ) { index ->
        val music = pagingItems[index]
        if (music != null) {
            MusicListItem(
                music = music,
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                onClick = { onMusicClick(music) },
                onMoreClick = { onMusicMoreClick(music) },
            )
        }
    }
}

@Composable
internal fun ArtistSearchResults(
    pagingItems: LazyPagingItems<Artist>,
    onArtistClick: (Long) -> Unit,
) {
    PagingResultList(
        pagingItems = pagingItems,
        emptyMessage = "未找到相关歌手",
    ) { index ->
        val artist = pagingItems[index]
        if (artist != null) {
            ArtistListItem(
                modifier = Modifier.padding(horizontal = 16.dp),
                artist = artist,
                onClick = { onArtistClick(artist.id) },
            )
        }
    }
}

@Composable
internal fun AlbumSearchResults(
    pagingItems: LazyPagingItems<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    PagingResultList(
        pagingItems = pagingItems,
        emptyMessage = "未找到相关专辑",
    ) { index ->
        val album = pagingItems[index]
        if (album != null) {
            AlbumListItem(
                modifier = Modifier.padding(horizontal = 16.dp),
                album = album,
                onClick = { onAlbumClick(album) },
            )
        }
    }
}

/** 外壳没注入帖子结果内容时的兜底（比如将来 iOS 端）。 */
@Composable
internal fun PostSearchResultsFallback(
    applyOverrides: (Post) -> Post,
    onPostLikeClick: (Post) -> Unit,
    onPostTagClick: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "当前平台暂不支持帖子搜索结果",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun <T : Any> PagingResultList(
    pagingItems: LazyPagingItems<T>,
    emptyMessage: String,
    itemContent: @Composable (index: Int) -> Unit,
) {
    when {
        pagingItems.loadState.refresh is LoadState.Loading -> {
            LoadingIndicator(useLottie = false)
        }

        pagingItems.loadState.refresh is LoadState.Error -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = (pagingItems.loadState.refresh as LoadState.Error).error.message
                    ?: "搜索失败",
                onRetry = { pagingItems.retry() },
            )
        }

        pagingItems.loadState.refresh is LoadState.NotLoading && pagingItems.itemCount == 0 -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = emptyMessage,
                showIcon = false,
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = { index ->
                        pagingItems.peek(index)?.let { item ->
                            (item as? Music)?.id?.toString()
                                ?: (item as? Artist)?.id?.toString()
                                ?: (item as? Album)?.id?.toString()
                                ?: (item as? Post)?.id?.toString()
                                ?: "search_item_$index"
                        } ?: "search_placeholder_$index"
                    },
                ) { index ->
                    itemContent(index)
                }

                when (pagingItems.loadState.append) {
                    is LoadState.Loading -> {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(useLottie = false)
                            }
                        }
                    }

                    is LoadState.Error -> {
                        item(key = "error_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "加载失败，点击重试",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { pagingItems.retry() },
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
