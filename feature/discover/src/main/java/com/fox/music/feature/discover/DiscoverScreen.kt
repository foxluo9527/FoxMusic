package com.fox.music.feature.discover

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.ui.components.MusicListItem

const val DISCOVER_ROUTE = "discover"

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String) -> Unit = {_, _,_ ->},
    updateMusicList: (List<Music>, String) -> Unit = {_, _ ->},
    onPlaylistClick: (Playlist) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { e ->
            when (e) {
                is DiscoverEffect.NavigateToMusic -> onMusicClick(e.music,emptyList(),DISCOVER_ROUTE)
                is DiscoverEffect.NavigateToPlaylist -> onPlaylistClick(e.playlist)
                is DiscoverEffect.NavigateToCategory -> {
                    viewModel.sendIntent(
                        DiscoverIntent.LoadPlayListByCategory(
                            e.category,
                            page = state.categoryPages[e.category]?.page ?: 1
                        )
                    )
                }
            }
        }
    }
//    Scaffold(modifier = Modifier.fillMaxSize(),topBar = {
//
//    }) {
//
//    }
    with(sharedTransitionScope) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (state.newMusic.isNotEmpty()) {
                item {
                    Text(
                        "New Music",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(state.newMusic.take(10), key = {it.id}) {music ->
                    MusicListItem(
                        music = music,
                        this@with,
                        animatedContentScope,
                        onClick = {viewModel.onMusicClick(music)})
                }
            }
            if (state.hotMusic.isNotEmpty()) {
                item {
                    Text(
                        "Hot",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                }
                items(state.hotMusic.take(10), key = {it.id}) {music ->
                    MusicListItem(
                        music = music,
                        this@with,
                        animatedContentScope,
                        onClick = {viewModel.onMusicClick(music)},
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            if (state.categoryPages.isNotEmpty()) {
                item {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    }
                }
            }
        }
    }

}
