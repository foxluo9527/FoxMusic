package com.fox.music.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/** 热门歌手页 Android 外壳。 */
@Composable
fun HotArtistListScreen(
    modifier: Modifier = Modifier,
    viewModel: HotArtistListViewModel = hiltViewModel(),
    onArtistClick: (Long) -> Unit = {},
    onAllArtistsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(HotArtistListIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is HotArtistListEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                HotArtistListEffect.NavigateToAllArtists -> onAllArtistsClick()
                HotArtistListEffect.NavigateBack -> onBackClick()
            }
        }
    }

    HotArtistListContent(
        state = state,
        onIntent = viewModel::sendIntent,
        modifier = modifier,
        onArtistClick = onArtistClick,
        onAllArtistsClick = onAllArtistsClick,
        onBackClick = onBackClick,
    )
}
