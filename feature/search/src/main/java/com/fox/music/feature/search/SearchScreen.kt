package com.fox.music.feature.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.Music
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxSearchBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem

const val SEARCH_ROUTE = "search"

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String) -> Unit = { _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()

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
    with(sharedTransitionScope) {
        Column(modifier = modifier.fillMaxSize()) {
            FoxSearchBar(
                query = state.query,
                onQueryChange = { viewModel.sendIntent(SearchIntent.QueryChange(it)) },
                onSearch = { viewModel.sendIntent(SearchIntent.Search) }
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.results.isEmpty() -> {
                        LoadingIndicator(useLottie = false)
                    }

                    state.error != null && state.results.isEmpty() && state.hasSearched -> {
                        ErrorView(
                            message = state.error!!,
                            onRetry = { viewModel.sendIntent(SearchIntent.Search) }
                        )
                    }

                    !state.hasSearched -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.hotKeywords.isNotEmpty()) {
                                item(key = "hot_header") {
                                    Text(
                                        text = "Hot",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                items(state.hotKeywords, key = { it }) { keyword ->
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.sendIntent(
                                                    SearchIntent.SelectHotKeyword(
                                                        keyword
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
                                        text = "Search History",
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

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(state.results, key = { it.id }) { music ->
                                MusicListItem(
                                    music = music,
                                    this@with,
                                    animatedContentScope,
                                    onClick = { viewModel.onMusicClick(music) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
