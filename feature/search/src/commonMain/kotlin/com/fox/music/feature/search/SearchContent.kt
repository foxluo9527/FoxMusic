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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.SearchBar

private val searchTabs = listOf(
    SearchResultTab.MUSIC to "单曲",
    SearchResultTab.ARTIST to "歌手",
    SearchResultTab.ALBUM to "专辑",
    SearchResultTab.POST to "帖子",
)

/**
 * 搜索页纯界面：state + onIntent 驱动。
 *
 * 三处平台相关逻辑留在外壳（:feature:search-android 的 SearchScreen）：
 *   1. BackHandler            —— androidx.activity，Android 专属
 *   2. LocalFocusManager      —— 软键盘收起交给外壳的 onClearFocus
 *   3. 帖子结果列表            —— PostNoteGridCard 依赖 PostVideoCover 等安卓组件，
 *                               由外壳以 postResultsContent 插槽注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMusicClick: (Music) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    onClearFocus: () -> Unit = {},
    onMusicListChanged: (List<Music>) -> Unit = {},
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    applyPostOverrides: (Post) -> Post = { it },
    onPostLikeClick: (Post) -> Unit = {},
    onPostTagClick: (String) -> Unit = {},
    postResultsContent: (@Composable () -> Unit)? = null,
) {
    val musicResults = state.musicResults.collectAsLazyPagingItems()
    val artistResults = state.artistResults.collectAsLazyPagingItems()
    val albumResults = state.albumResults.collectAsLazyPagingItems()
    val playlistKey = SEARCH_ROUTE + "-${state.query}"
    val showRecommendPanel = state.showRecommendPanel
    val canDismissToResults = state.hasSearched && state.isSearchFocused

    fun handleBack() {
        if (canDismissToResults) {
            onClearFocus()
            onIntent(SearchIntent.FocusChanged(false))
        } else {
            onBack()
        }
    }

    with(sharedTransitionScope) {
        Column(
            modifier = modifier.fillMaxSize(),
        ) {
            TopAppBar(
                title = {
                    SearchBar(
                        query = state.query,
                        onQueryChange = { onIntent(SearchIntent.QueryChange(it)) },
                        onSearch = { onIntent(SearchIntent.Search) },
                        onFocusChanged = { onIntent(SearchIntent.FocusChanged(it)) },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            val showResultsChrome = state.hasSearched && !showRecommendPanel

            if (showResultsChrome) {
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
                            onClick = { onIntent(SearchIntent.SelectTab(tab)) },
                            text = { Text(label) },
                        )
                    }
                }
            }

            if (showResultsChrome && state.selectedTab == SearchResultTab.MUSIC) {
                MusicPlatformSelector(
                    selected = state.selectedMusicPlatform,
                    onSelect = { onIntent(SearchIntent.SelectMusicPlatform(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    showRecommendPanel -> RecommendOrSuggestContent(
                        query = state.query,
                        suggestions = state.suggestions,
                        hotKeywords = state.hotKeywords,
                        hotKeywordsHasMore = state.hotKeywordsHasMore,
                        isLoadingHotKeywords = state.isLoadingHotKeywords,
                        searchHistory = state.searchHistory,
                        isHistoryExpanded = state.isHistoryExpanded,
                        onHotKeywordClick = { onIntent(SearchIntent.SelectHotKeyword(it)) },
                        onRefreshHotKeywords = { onIntent(SearchIntent.RefreshHotKeywords) },
                        onHistoryClick = { onIntent(SearchIntent.SelectHistory(it)) },
                        onSuggestClick = { onIntent(SearchIntent.SelectSuggest(it)) },
                        onToggleHistoryExpanded = {
                            onIntent(SearchIntent.ToggleHistoryExpanded)
                        },
                        onClearHistory = { onIntent(SearchIntent.ClearHistory) },
                    )

                    state.selectedTab == SearchResultTab.MUSIC -> MusicSearchResults(
                        pagingItems = musicResults,
                        sharedTransitionScope = this@with,
                        animatedContentScope = animatedContentScope,
                        onMusicClick = onMusicClick,
                        onMusicMoreClick = onMusicMoreClick,
                        onArtistClick = onArtistClick,
                        onMusicListChanged = onMusicListChanged,
                        updateMusicList = updateMusicList,
                        playlistKey = playlistKey,
                        hasSearched = state.hasSearched,
                    )

                    state.selectedTab == SearchResultTab.ARTIST -> ArtistSearchResults(
                        pagingItems = artistResults,
                        onArtistClick = onArtistClick,
                    )

                    state.selectedTab == SearchResultTab.ALBUM -> AlbumSearchResults(
                        pagingItems = albumResults,
                        onAlbumClick = onAlbumClick,
                    )

                    state.selectedTab == SearchResultTab.POST -> {
                        postResultsContent?.invoke() ?: PostSearchResultsFallback(
                            applyOverrides = applyPostOverrides,
                            onPostLikeClick = onPostLikeClick,
                            onPostTagClick = onPostTagClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlatformSelector(
    selected: SearchMusicPlatform,
    onSelect: (SearchMusicPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchMusicPlatform.entries.forEach { platform ->
            FilterChip(
                selected = selected == platform,
                onClick = { onSelect(platform) },
                label = { Text(platform.label) },
            )
        }
    }
}

@Composable
private fun RecommendOrSuggestContent(
    query: String,
    suggestions: List<SearchSuggest>,
    hotKeywords: List<HotKeyword>,
    hotKeywordsHasMore: Boolean,
    isLoadingHotKeywords: Boolean,
    searchHistory: List<SearchHistory>,
    isHistoryExpanded: Boolean,
    onHotKeywordClick: (String) -> Unit,
    onRefreshHotKeywords: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onSuggestClick: (SearchSuggest) -> Unit,
    onToggleHistoryExpanded: () -> Unit,
    onClearHistory: () -> Unit,
) {
    when {
        suggestions.isNotEmpty() -> SearchSuggestList(
            suggestions = suggestions,
            onSuggestClick = onSuggestClick,
        )

        query.trim().length < 2 -> SearchRecommendContent(
            hotKeywords = hotKeywords,
            hotKeywordsHasMore = hotKeywordsHasMore,
            isLoadingHotKeywords = isLoadingHotKeywords,
            searchHistory = searchHistory,
            isHistoryExpanded = isHistoryExpanded,
            onHotKeywordClick = onHotKeywordClick,
            onRefreshHotKeywords = onRefreshHotKeywords,
            onHistoryClick = onHistoryClick,
            onToggleHistoryExpanded = onToggleHistoryExpanded,
            onClearHistory = onClearHistory,
        )

        else -> {
            // 已触发建议请求但返回空：清空上一屏推荐内容
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SearchSuggestList(
    suggestions: List<SearchSuggest>,
    onSuggestClick: (SearchSuggest) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(
            items = suggestions,
            key = { "${it.type}_${it.id}_${it.text}" },
        ) { suggest ->
            SearchSuggestRow(
                suggest = suggest,
                onClick = { onSuggestClick(suggest) },
            )
        }
    }
}

@Composable
private fun SearchSuggestRow(
    suggest: SearchSuggest,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = suggest.cover,
            contentDescription = suggest.text,
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggest.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestTypeLabel(suggest.type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun suggestTypeLabel(type: String): String = when (type.lowercase()) {
    "music" -> "音乐"
    "artist" -> "艺人"
    "album" -> "专辑"
    "video" -> "视频"
    "novel" -> "小说"
    "post" -> "动态"
    else -> type.ifBlank { "搜索" }
}

@Composable
private fun SearchRecommendContent(
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
        historyKeywords.take(SEARCH_HISTORY_COLLAPSED_MAX)
    }
    val canExpandHistory = historyKeywords.size > SEARCH_HISTORY_COLLAPSED_MAX

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
                    maxRows = if (isHistoryExpanded) null else SEARCH_HISTORY_COLLAPSED_ROWS,
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
                    maxRows = SEARCH_HOT_KEYWORD_MAX_ROWS,
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
