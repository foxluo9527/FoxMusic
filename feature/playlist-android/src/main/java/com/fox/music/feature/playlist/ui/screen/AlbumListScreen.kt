package com.fox.music.feature.playlist.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.feature.playlist.ui.screen.AlbumListContent
import com.fox.music.feature.playlist.viewmodel.AlbumListEffect
import com.fox.music.feature.playlist.viewmodel.AlbumListIntent
import com.fox.music.feature.playlist.viewmodel.AlbumListViewModel

/** 推荐专辑页 Android 外壳：hiltViewModel() + effect 消费，界面在 [AlbumListContent]。 */
@Composable
fun AlbumListScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: AlbumListViewModel = hiltViewModel(),
    onAlbumClick: (Album) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(AlbumListIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is AlbumListEffect.NavigateToAlbum -> onAlbumClick(effect.album)
                is AlbumListEffect.NavigateBack -> onBackClick()
            }
        }
    }

    AlbumListContent(
        state = state,
        onIntent = viewModel::sendIntent,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
        onAlbumClick = viewModel::onAlbumClick,
        onBackClick = { viewModel.onBackClick() },
    )
}
