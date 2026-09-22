package com.fox.music.feature.playlist.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Playlist
import com.fox.music.feature.playlist.ui.component.PlaylistListContent
import com.fox.music.feature.playlist.viewmodel.PlaylistListEffect
import com.fox.music.feature.playlist.viewmodel.PlaylistListIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistListViewModel

/** 歌单广场页 Android 外壳：hiltViewModel() + effect 消费，界面在 [PlaylistListContent]。 */
@Composable
fun PlaylistListScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: PlaylistListViewModel = hiltViewModel(),
    onPlaylistClick: (Playlist) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(PlaylistListIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is PlaylistListEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlist)
                is PlaylistListEffect.NavigateBack -> onBackClick()
            }
        }
    }

    PlaylistListContent(
        state = state,
        onIntent = viewModel::sendIntent,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
        onPlaylistClick = viewModel::onPlaylistClick,
        onBackClick = { viewModel.onBackClick() },
    )
}
