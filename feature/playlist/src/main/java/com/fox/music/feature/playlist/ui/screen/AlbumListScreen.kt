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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.playlist.viewmodel.AlbumListEffect
import com.fox.music.feature.playlist.viewmodel.AlbumListIntent
import com.fox.music.feature.playlist.viewmodel.AlbumListViewModel

const val ALBUM_LIST_ROUTE = "album_list"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: AlbumListViewModel = hiltViewModel(),
    onAlbumClick: (Album) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val albumPagingItems = state.albums.collectAsLazyPagingItems()

    // Effect handlers
    LaunchedEffect(Unit) {
        viewModel.sendIntent(AlbumListIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is AlbumListEffect.NavigateToAlbum -> onAlbumClick(effect.album)
                is AlbumListEffect.NavigateBack -> onBackClick()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = {
                Text("推荐专辑")
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.onBackClick() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "返回"
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )

        // Content
        when {
            albumPagingItems.loadState.refresh == LoadState.Loading -> {
                LoadingIndicator(useLottie = false)
            }

            albumPagingItems.loadState.refresh is LoadState.Error -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = (albumPagingItems.loadState.refresh as LoadState.Error).error.message
                        ?: "加载失败",
                    onRetry = { viewModel.sendIntent(AlbumListIntent.Load) }
                )
            }

            albumPagingItems.loadState.refresh is LoadState.NotLoading && albumPagingItems.itemCount == 0 -> {
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
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = albumPagingItems.itemCount,
                            key = { index -> albumPagingItems[index]?.id ?: index },
                            contentType = { "album_item" }
                        ) {
                                index ->
                            albumPagingItems[index]?.let { album ->
                                AlbumGridItem(
                                    album = album,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    onClick = { viewModel.onAlbumClick(album) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 专辑网格项组件 (复用自 HomeScreen)
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