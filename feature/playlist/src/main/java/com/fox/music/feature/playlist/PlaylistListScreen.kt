package com.fox.music.feature.playlist

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
import com.fox.music.core.ui.components.LoadingIndicator
import com.fox.music.core.ui.components.PlaylistCard

const val PLAYLIST_LIST_ROUTE = "playlist_list"
const val PLAYLIST_DETAIL_ROUTE = "playlist_detail/{playlistId}"

fun playlistDetailRoute(playlistId: Long) = "playlist_detail/$playlistId"

@Composable
fun PlaylistListScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: PlaylistListViewModel = hiltViewModel(),
    onPlaylistClick: (com.fox.music.core.model.Playlist) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { when (it) {
            is PlaylistListEffect.NavigateToPlaylist -> onPlaylistClick(it.playlist)
        } }
    }
    if (state.isLoading) {
        LoadingIndicator(useLottie = false)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.minePlayList.isNotEmpty()) {
            item {
                Text("My Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.minePlayList, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { viewModel.onPlaylistClick(playlist) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope
                        )
                    }
                }
            }
        }
        if (state.recommendedPlaylists.isNotEmpty()) {
            item {
                Text("Recommended", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp, top = 8.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recommendedPlaylists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { viewModel.onPlaylistClick(playlist) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope
                        )
                    }
                }
            }
        }
        if (state.minePlayList.isEmpty() && state.recommendedPlaylists.isEmpty()){

        }
    }
}
