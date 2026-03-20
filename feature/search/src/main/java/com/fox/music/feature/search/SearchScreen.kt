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
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.ui.components.SearchBar
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem

const val SEARCH_ROUTE = "search"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String) -> Unit = { _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val searchResults = state.results.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    emptyList(),
                    SEARCH_ROUTE + "-${state.query}"
                )
            }
        }
    }
    LaunchedEffect(searchResults.itemSnapshotList) {
        updateMusicList(searchResults.itemSnapshotList.items, SEARCH_ROUTE + "-${state.query}")
    }
    with(sharedTransitionScope) {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    SearchBar(
                        query = state.query,
                        onQueryChange = { viewModel.sendIntent(SearchIntent.QueryChange(it)) },
                        onSearch = { viewModel.sendIntent(SearchIntent.Search) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // 未搜索状态：显示热词和历史记录
                    !state.hasSearched -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.hotKeywords.isNotEmpty()) {
                                item(key = "hot_header") {
                                    Text(
                                        text = "热门搜索",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                items(state.hotKeywords, key = { it.keyword }) { keyword ->
                                    Text(
                                        text = keyword.keyword,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.sendIntent(
                                                    SearchIntent.SelectHotKeyword(
                                                        keyword.keyword
                                                    )
                                                )
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                            if (state.searchHistory.isNotEmpty()) {
                                item(key = "history_header") {
                                    Text(
                                        text = "搜索历史",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                    )
                                }
                                items(state.searchHistory, key = { it.id }) { item ->
                                    Text(
                                        text = item.keyword,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.sendIntent(
                                                    SearchIntent.SelectHistory(
                                                        item.keyword
                                                    )
                                                )
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 初次加载中
                    searchResults.loadState.refresh is LoadState.Loading -> {
                        LoadingIndicator(useLottie = false)
                    }

                    // 加载错误
                    searchResults.loadState.refresh is LoadState.Error -> {
                        ErrorView(
                            modifier = Modifier.fillMaxSize(),
                            message = (searchResults.loadState.refresh as LoadState.Error).error.message
                                ?: "搜索失败",
                            onRetry = { searchResults.retry() }
                        )
                    }

                    // 无结果
                    searchResults.loadState.refresh is LoadState.NotLoading && searchResults.itemCount == 0 -> {
                        ErrorView(
                            modifier = Modifier.fillMaxSize(),
                            message = "未找到相关结果",
                            showIcon = false
                        )
                    }

                    // 显示搜索结果
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(
                                count = searchResults.itemCount,
                                key = { index -> searchResults[index]?.id ?: "search_item_$index" }
                            ) { index ->
                                val music = searchResults[index]
                                if (music != null) {
                                    MusicListItem(
                                        music = music,
                                        this@with,
                                        animatedContentScope,
                                        onClick = { viewModel.onMusicClick(music) }
                                    )
                                }
                            }

                            // 加载更多状态
                            when (searchResults.loadState.append) {
                                is LoadState.Loading -> {
                                    item(key = "loading_more") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
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
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "加载失败，点击重试",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { searchResults.retry() }
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
        }
    }
}
