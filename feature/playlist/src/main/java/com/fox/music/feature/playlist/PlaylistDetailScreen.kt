package com.fox.music.feature.playlist

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.fox.music.core.ui.components.CachedImage
import com.fox.music.core.ui.components.ErrorView
import com.fox.music.core.ui.components.LoadingIndicator
import com.fox.music.core.ui.components.MusicListItem

@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String) -> Unit = {_, _, _ ->},
    updateMusicList: (List<Music>, String) -> Unit = {_, _ ->},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { when (it) {
            is PlaylistDetailEffect.NavigateToMusic -> onMusicClick(
                it.music, state.detail?.tracks?.list ?: emptyList(),
                "playlist_detail/" + state.detail?.playlist?.id?.toString()
            )
        } }
    }
    when {
        state.isLoading && state.detail == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.detail == null -> ErrorView(message = state.error!!)
        else -> state.detail?.let { detail ->
            with(sharedTransitionScope){
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        CachedImage(
                            imageUrl = detail.playlist.coverImage,
                            contentDescription = detail.playlist.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(detail.playlist.title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${detail.playlist.trackCount} tracks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(detail.tracks.list, key = { it.id }) { music ->
                        MusicListItem(music = music,this@with,animatedContentScope, onClick = { viewModel.onMusicClick(music) })
                    }
                }
            }
        }
    }
}
