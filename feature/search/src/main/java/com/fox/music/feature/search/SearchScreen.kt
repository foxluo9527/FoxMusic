package com.fox.music.feature.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onMusicMoreClick: (Music) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val musicResults = state.musicResults.collectAsLazyPagingItems()
    val artistResults = state.artistResults.collectAsLazyPagingItems()
    val albumResults = state.albumResults.collectAsLazyPagingItems()
    val playlistKey = SEARCH_ROUTE + "-${state.query}"

    LaunchedEffect(musicResults.itemCount) {
        if (state.hasSearched && state.selectedTab == SearchResultTab.MUSIC) {
            viewModel.updateCurrentMusicList(musicResults.itemSnapshotList.items)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToMusic -> {
                    val playlist = effect.musicList.ifEmpty { listOf(effect.music) }
                    onMusicClick(effect.music, playlist, playlistKey)
                }

                is SearchEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                is SearchEffect.NavigateToAlbum -> onAlbumClick(effect.album)
            }
        }
    }

    LaunchedEffect(
        musicResults.itemSnapshotList.items, state.selectedTab, state.hasSearched, playlistKey
    ) {
        if (state.hasSearched && state.selectedTab == SearchResultTab.MUSIC) {
            val items = musicResults.itemSnapshotList.items
            viewModel.updateCurrentMusicList(items)
            if (items.isNotEmpty()) {
                updateMusicList(items, playlistKey)
            }
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
                        hotKeywordsHasMore = state.hotKeywordsHasMore,
                        isLoadingHotKeywords = state.isLoadingHotKeywords,
                        searchHistory = state.searchHistory,
                        isHistoryExpanded = state.isHistoryExpanded,
                        onHotKeywordClick = { viewModel.sendIntent(SearchIntent.SelectHotKeyword(it)) },
                        onRefreshHotKeywords = { viewModel.sendIntent(SearchIntent.RefreshHotKeywords) },
                        onHistoryClick = { viewModel.sendIntent(SearchIntent.SelectHistory(it)) },
                        onToggleHistoryExpanded = { viewModel.sendIntent(SearchIntent.ToggleHistoryExpanded) },
                        onClearHistory = { viewModel.sendIntent(SearchIntent.ClearHistory) },
                    )

                    state.selectedTab == SearchResultTab.MUSIC -> MusicSearchResults(
                        musicResults = musicResults,
                        sharedTransitionScope = this@with,
                        animatedContentScope = animatedContentScope,
                        onMusicClick = { viewModel.onMusicClick(it) },
                        onMusicMoreClick = onMusicMoreClick,
                        onArtistClick = onArtistClick,
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
    hotKeywordsHasMore: Boolean,
    isLoadingHotKeywords: Boolean,
    searchHistory: List<SearchHistory>,
    isHistoryExpanded: Boolean,
    onHotKeywordClick: (String) -> Unit,
    onRefreshHotKeywords: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onToggleHistoryExpanded: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val historyKeywords = searchHistory.map { it.keyword }.distinct()
    val visibleHistory = if (isHistoryExpanded) {
        historyKeywords
    } else {
        historyKeywords.take(SearchViewModel.HISTORY_COLLAPSED_MAX)
    }
    val canExpandHistory = historyKeywords.size > SearchViewModel.HISTORY_COLLAPSED_MAX

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (searchHistory.isNotEmpty()) {
            SearchSuggestionSection(
                title = "搜索历史",
                trailingContent = {
                    if (canExpandHistory) {
                        TextButton(onClick = onToggleHistoryExpanded) {
                            Icon(
                                imageVector = if (isHistoryExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = if (isHistoryExpanded) "收起" else "展开",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isHistoryExpanded) "收起" else "展开",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    TextButton(onClick = onClearHistory) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "清空",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "清空",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            ) {
                TwoColumnKeywordGrid(
                    keywords = visibleHistory,
                    maxRows = if (isHistoryExpanded) null else SearchViewModel.HISTORY_COLLAPSED_ROWS,
                    onKeywordClick = onHistoryClick,
                )
            }
        }

        if (hotKeywords.isNotEmpty()) {
            SearchSuggestionSection(
                title = "推荐搜索",
                trailingContent = {
                    if (hotKeywordsHasMore) {
                        val refreshTransition = rememberInfiniteTransition(label = "refresh_spin")
                        val refreshRotation by refreshTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                            ),
                            label = "refresh_rotation",
                        )
                        TextButton(
                            onClick = onRefreshHotKeywords,
                            enabled = !isLoadingHotKeywords,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "换一换",
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(if (isLoadingHotKeywords) refreshRotation else 0f),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "换一换",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
            ) {
                TwoColumnKeywordGrid(
                    keywords = hotKeywords.map { it.keyword },
                    maxRows = SearchViewModel.HOT_KEYWORD_MAX_ROWS,
                    onKeywordClick = onHotKeywordClick,
                )
            }
        }
    }
}

@Composable
private fun SearchSuggestionSection(
    title: String,
    trailingContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailingContent()
            }
        }
        content()
    }
}

@Composable
private fun TwoColumnKeywordGrid(
    keywords: List<String>,
    maxRows: Int?,
    onKeywordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayKeywords = if (maxRows != null) {
        keywords.take(maxRows * 2)
    } else {
        keywords
    }
    val rows = displayKeywords.chunked(2)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowKeywords ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowKeywords.forEach { keyword ->
                    KeywordChip(
                        text = keyword,
                        onClick = { onKeywordClick(keyword) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowKeywords.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeywordChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MusicSearchResults(
    musicResults: LazyPagingItems<Music>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onArtistClick: (Long) -> Unit,
) {
    PagingResultList(
        pagingItems = musicResults,
        emptyMessage = "未找到相关单曲",
    ) { index ->
        val music = musicResults[index]
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
                modifier = Modifier.padding(horizontal = 16.dp),
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
                modifier = Modifier.padding(horizontal = 16.dp),
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
