package com.fox.music.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/** 全部歌手页 Android 外壳。 */
@Composable
fun ArtistListScreen(
    modifier: Modifier = Modifier,
    viewModel: ArtistListViewModel = hiltViewModel(),
    onArtistClick: (Long) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(ArtistListIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is ArtistListEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                ArtistListEffect.NavigateBack -> onBackClick()
            }
        }
    }

    ArtistListContent(
        state = state,
        onIntent = viewModel::sendIntent,
        modifier = modifier,
        onArtistClick = onArtistClick,
        onBackClick = onBackClick,
    )
}
