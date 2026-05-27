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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.ui.components.SearchBar
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem

const val SEARCH_ROUTE = "search"

private val searchTabs = listOf(
    SearchResultTab.MUSIC to "单曲",
    SearchResultTab.ARTIST to "歌手",
    SearchResultTab.ALBUM to "专辑",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String) -> Unit = { _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onArtistClick: (Long) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val musicResults = state.musicResults.collectAsLazyPagingItems()
    val artistResults = state.artistResults.collectAsLazyPagingItems()
    val albumResults = state.albumResults.collectAsLazyPagingItems()
    val playlistKey = SEARCH_ROUTE + "-${state.query}"

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToMusic -> {
                    val playlist = (0 until musicResults.itemCount)
                        .mapNotNull { musicResults[it] }
                    onMusicClick(effect.music, playlist, playlistKey)
                }
                is SearchEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                is SearchEffect.NavigateToAlbum -> onAlbumClick(effect.album)
            }
        }
    }

    LaunchedEffect(musicResults.itemSnapshotList, state.selectedTab, state.hasSearched) {
        if (state.hasSearched && state.selectedTab == SearchResultTab.MUSIC) {
            updateMusicList(musicResults.itemSnapshotList.items, playlistKey)
        }
    }

    with(sharedTransitionScope) {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    SearchBar(
                        query = state.query,
                        onQueryChange = { viewModel.sendIntent(SearchIntent.QueryChange(it)) },
                        onSearch = { viewModel.sendIntent(SearchIntent.Search) },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            if (state.hasSearched) {
                ScrollableTabRow(
                    selectedTabIndex = searchTabs.indexOfFirst { it.first == state.selectedTab }
                        .coerceAtLeast(0),
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                    divider = {},
                ) {
                    searchTabs.forEach { (tab, label) ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.sendIntent(SearchIntent.SelectTab(tab)) },
                            text = { Text(label) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !state.hasSearched -> SearchSuggestionsContent(
                        hotKeywords = state.hotKeywords,
                        searchHistory = state.searchHistory,
                        onHotKeywordClick = { viewModel.sendIntent(SearchIntent.SelectHotKeyword(it)) },
                        onHistoryClick = { viewModel.sendIntent(SearchIntent.SelectHistory(it)) },
                    )

                    state.selectedTab == SearchResultTab.MUSIC -> MusicSearchResults(
                        musicResults = musicResults,
                        sharedTransitionScope = this@with,
                        animatedContentScope = animatedContentScope,
                        onMusicClick = { viewModel.onMusicClick(it) },
                    )

                    state.selectedTab == SearchResultTab.ARTIST -> ArtistSearchResults(
                        artistResults = artistResults,
                        onArtistClick = { viewModel.onArtistClick(it) },
                    )

                    state.selectedTab == SearchResultTab.ALBUM -> AlbumSearchResults(
                        albumResults = albumResults,
                        onAlbumClick = { viewModel.onAlbumClick(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionsContent(
    hotKeywords: List<HotKeyword>,
    searchHistory: List<SearchHistory>,
    onHotKeywordClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hotKeywords.isNotEmpty()) {
            item(key = "hot_header") {
                Text(
                    text = "热门搜索",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(hotKeywords, key = { it.keyword }) { keyword ->
                Text(
                    text = keyword.keyword,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHotKeywordClick(keyword.keyword) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
        if (searchHistory.isNotEmpty()) {
            item(key = "history_header") {
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(searchHistory, key = { it.id }) { item ->
                Text(
                    text = item.keyword,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHistoryClick(item.keyword) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MusicSearchResults(
    musicResults: LazyPagingItems<Music>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
) {
  PagingResultList(
        pagingItems = musicResults,
        emptyMessage = "未找到相关单曲",
    ) { index ->
        val music = musicResults[index]
        if (music != null) {
            MusicListItem(
                music = music,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                onClick = { onMusicClick(music) },
            )
        }
    }
}

@Composable
private fun ArtistSearchResults(
    artistResults: LazyPagingItems<Artist>,
    onArtistClick: (Long) -> Unit,
) {
    PagingResultList(
        pagingItems = artistResults,
        emptyMessage = "未找到相关歌手",
    ) { index ->
        val artist = artistResults[index]
        if (artist != null) {
            ArtistListItem(
                artist = artist,
                onClick = { onArtistClick(artist.id) },
            )
        }
    }
}

@Composable
private fun AlbumSearchResults(
    albumResults: LazyPagingItems<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    PagingResultList(
        pagingItems = albumResults,
        emptyMessage = "未找到相关专辑",
    ) { index ->
        val album = albumResults[index]
        if (album != null) {
            AlbumListItem(
                album = album,
                onClick = { onAlbumClick(album) },
            )
        }
    }
}

@Composable
private fun <T : Any> PagingResultList(
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
                    key = { index -> "search_item_$index" },
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
