package com.fox.music.feature.playlist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.fox.music.core.ui.components.CachedImage
import androidx.compose.foundation.lazy.LazyColumn

@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onMusicClick: (com.fox.music.core.model.Music) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { when (it) {
            is PlaylistDetailEffect.NavigateToMusic -> onMusicClick(it.music)
        } }
    }
    when {
        state.isLoading && state.detail == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.detail == null -> ErrorView(message = state.error!!)
        else -> state.detail?.let { detail ->
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
                    MusicListItem(music = music, onClick = { viewModel.onMusicClick(music) })
                }
            }
        }
    }
}
