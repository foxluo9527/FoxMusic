package com.fox.music.feature.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.component.JumpToPlayingFabForList
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.LocalJumpHighlight
import com.fox.music.core.ui.component.LocalNowPlayingMusicId
import com.fox.music.core.ui.component.jumpHighlight
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.util.formatTrackDuration
import kotlinx.coroutines.launch

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
    onCollectClick: () -> Unit = {},
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

    // 全局播放态（app 层 provide）；没有时退化为「刚点过的那一行」，
    // 保证从搜索结果起播后本页也能立刻显示正在播放。
    val nowPlayingId = LocalNowPlayingMusicId.current
    val jumpHighlight = LocalJumpHighlight.current
    var playingMusicId by remember { mutableStateOf<Long?>(null) }
    val effectivePlayingId = nowPlayingId ?: playingMusicId

    val listState = rememberLazyListState()
    val jumpScope = rememberCoroutineScope()
    val targetPagingIndex = remember(nowPlayingId, pagingItems.itemCount) {
        if (nowPlayingId == null) {
            -1
        } else {
            var idx = -1
            for (i in 0 until pagingItems.itemCount) {
                if (pagingItems.peek(i)?.id == nowPlayingId) {
                    idx = i
                    break
                }
            }
            idx
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PagingResultList(
            pagingItems = pagingItems,
            emptyMessage = "未找到相关单曲",
            listState = listState,
            emptyAction = { SearchCollectFooterButton(onCollectClick) },
            footer = {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { SearchCollectFooterButton(onCollectClick) }
            },
            itemContent = { index ->
                val music = pagingItems[index]
                if (music != null) {
                    FoxEditorialRow(
                        title = music.title,
                        number = (index + 1).toString(),
                        modifier = Modifier.jumpHighlight(music.id),
                        leading = {
                            CachedImage(
                                imageUrl = music.coverImage,
                                contentDescription = music.title,
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(9.dp),
                            )
                        },
                        subtitle = music.artists.joinToString { it.name }
                            .takeIf { it.isNotBlank() },
                        isPlaying = effectivePlayingId == music.id,
                        trailing = {
                            Text(
                                text = formatTrackDuration(music.duration),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = InkQuaternary,
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onMusicMoreClick(music) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "更多",
                                    modifier = Modifier.size(18.dp),
                                    tint = InkQuaternary,
                                )
                            }
                        },
                        onClick = {
                            playingMusicId = music.id
                            onMusicClick(music)
                        },
                    )
                }
            },
        )

        JumpToPlayingFabForList(
            listState = listState,
            targetIndex = targetPagingIndex,
            onClick = {
                jumpScope.launch {
                    listState.animateScrollToItem(targetPagingIndex)
                    // 到位后再闪一下，强调「就是这一行」
                    jumpHighlight.flash(nowPlayingId)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
    }
}

/** 列表底部/空态下的「去收录」入口（第三方平台搜索→收录到平台） */
@Composable
private fun SearchCollectFooterButton(onCollectClick: () -> Unit) {
    OutlinedButton(
        onClick = onCollectClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("没有想要的结果，去收录")
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
        itemContent = { index ->
            val artist = pagingItems[index]
            if (artist != null) {
                FoxEditorialRow(
                    title = artist.name,
                    number = (index + 1).toString(),
                    leading = {
                        CachedImage(
                            imageUrl = artist.avatar ?: artist.coverImage,
                            contentDescription = artist.name,
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(26.dp),
                        )
                    },
                    subtitle = "${artist.musicCount} 首作品".takeIf { artist.musicCount > 0 },
                    onClick = { onArtistClick(artist.id) },
                )
            }
        },
    )
}

@Composable
internal fun AlbumSearchResults(
    pagingItems: LazyPagingItems<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    PagingResultList(
        pagingItems = pagingItems,
        emptyMessage = "未找到相关专辑",
        itemContent = { index ->
            val album = pagingItems[index]
            if (album != null) {
                FoxEditorialRow(
                    title = album.title,
                    number = (index + 1).toString(),
                    leading = {
                        CachedImage(
                            imageUrl = album.coverImage,
                            contentDescription = album.title,
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(9.dp),
                        )
                    },
                    subtitle = album.artists.joinToString { it.name }
                        .takeIf { it.isNotBlank() },
                    trailing = {
                        Text(
                            text = formatTrackDuration(album.duration),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = InkQuaternary,
                        )
                    },
                    onClick = { onAlbumClick(album) },
                )
            }
        },
    )
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
    emptyAction: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    listState: LazyListState? = null,
) {
    val fallbackListState = rememberLazyListState()
    val resolvedListState = listState ?: fallbackListState
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 注意：ErrorView 默认 fillMaxSize，必须显式覆盖为 wrapContent，
                // 否则它会吃满整个 Column，把下面的 emptyAction 挤出屏幕（用户反馈看不到按钮）
                ErrorView(
                    message = emptyMessage,
                    showIcon = false,
                    modifier = Modifier.padding(16.dp),
                )
                emptyAction?.invoke()
            }
        }

        else -> {
            LazyColumn(
                state = resolvedListState,
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

                if (footer != null) {
                    item(key = "collect_footer") {
                        footer()
                    }
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
