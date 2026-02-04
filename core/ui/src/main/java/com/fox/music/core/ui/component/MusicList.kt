package com.fox.music.core.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.Music

/**
 *    Author : 罗福林
 *    Date   : 2026/2/3
 *    Desc   :
 */
@Composable
fun MusicList(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    pagingItems: LazyPagingItems<Music>,
    onMusicClick: (Music) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        val refresh = pagingItems.loadState.refresh
        val loadMore = pagingItems.loadState.append
        when(refresh) {
            is LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            is LoadState.Error -> {
                ErrorView(
                    message = refresh.error.message ?: "",
                    onRetry = {
                        pagingItems.refresh()
                    }
                )
            }

            else -> {
                if (pagingItems.itemCount == 0) {
                    EmptyView(
                        message = "No music found",
                        subtitle = "Try searching for something else",
                    )
                } else {
                    Text(
                        text = "Recommended for You",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(
                            pagingItems.itemCount,
                            key = pagingItems.itemKey {it.id}
                        ) {index ->
                            pagingItems[index]?.let {music ->
                                MusicListItem(
                                    music = music,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    onClick = {onMusicClick(music)}
                                )
                            }
                        }
                        when(loadMore) {
                            is LoadState.Loading -> {
                                item("loadingMore") {
                                    LoadingIndicator(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(60.dp), true
                                    )
                                }
                            }

                            is LoadState.Error -> {
                                item("loadMoreFailed") {
                                    ErrorView(
                                        Modifier
                                            .fillMaxWidth(),
                                        "加载失败，请重试",
                                        showIcon = false,
                                        onRetry = {pagingItems.retry()})
                                }
                            }

                            is LoadState.NotLoading -> {
                                if (loadMore.endOfPaginationReached) {
                                    item {
                                        Text("人家也是有底线的")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}