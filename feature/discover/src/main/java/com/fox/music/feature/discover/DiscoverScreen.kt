package com.fox.music.feature.discover

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
import com.fox.music.core.ui.components.MusicListItem
import com.fox.music.core.ui.components.PlaylistCard

const val DISCOVER_ROUTE = "discover"

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    onMusicClick: (com.fox.music.core.model.Music) -> Unit = {},
    onPlaylistClick: (com.fox.music.core.model.Playlist) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { e ->
            when (e) {
                is DiscoverEffect.NavigateToMusic -> onMusicClick(e.music)
                is DiscoverEffect.NavigateToPlaylist -> onPlaylistClick(e.playlist)
                is DiscoverEffect.NavigateToCategory -> { }
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (state.newMusic.isNotEmpty()) {
            item {
                Text("New Music", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(state.newMusic.take(10), key = { it.id }) { music ->
                MusicListItem(music = music, onClick = { viewModel.onMusicClick(music) })
            }
        }
        if (state.hotMusic.isNotEmpty()) {
            item {
                Text("Hot", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp, top = 8.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.hotMusic.take(10), key = { it.id }) { music ->
                        MusicListItem(music = music, onClick = { viewModel.onMusicClick(music) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    }
}
