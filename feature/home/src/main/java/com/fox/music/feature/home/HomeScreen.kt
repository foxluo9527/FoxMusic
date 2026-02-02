package com.fox.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.fox.music.core.ui.components.ErrorView
import com.fox.music.core.ui.components.LoadingIndicator
import com.fox.music.core.ui.components.MusicListItem
import com.fox.music.core.ui.components.PlaylistCard

const val HOME_ROUTE = "home"

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onMusicClick: (com.fox.music.core.model.Music) -> Unit = {},
    onPlaylistClick: (com.fox.music.core.model.Playlist) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToMusic -> onMusicClick(effect.music)
                is HomeEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlist)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.recommendedMusic.isEmpty() -> {
                LoadingIndicator(useLottie = false)
            }
            state.error != null && state.recommendedMusic.isEmpty() -> {
                ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.sendIntent(HomeIntent.Refresh) }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (state.recommendedPlaylists.isNotEmpty()) {
                        item(key = "playlists_header") {
                            Text(
                                text = "Recommended Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        item(key = "playlists_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    state.recommendedPlaylists,
                                    key = { it.id }
                                ) { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = { viewModel.onPlaylistClick(playlist) }
                                    )
                                }
                            }
                        }
                    }
                    if (state.recommendedMusic.isNotEmpty()) {
                        item(key = "music_header") {
                            Text(
                                text = "Recommended for You",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(
                            state.recommendedMusic,
                            key = { it.id }
                        ) { music ->
                            MusicListItem(
                                music = music,
                                onClick = { viewModel.onMusicClick(music) }
                            )
                        }
                    }
                }
            }
        }
    }
}
