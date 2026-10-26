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

    // 这里只收集 effect，不再发 Load —— 首次加载在 ViewModel init 里做。
    // 之前 Load 放在 LaunchedEffect(Unit) 里，每次返回都会重建分页数据并清空列表。
    LaunchedEffect(Unit) {
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
        initialFirstVisibleItemIndex = viewModel.scrollState.firstVisibleItemIndex(),
        initialFirstVisibleItemScrollOffset = viewModel.scrollState.firstVisibleItemScrollOffset(),
        onScrollPositionChange = viewModel.scrollState::save,
    )
}
