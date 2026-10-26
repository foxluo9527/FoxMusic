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

    // 只收集 effect，不再发 Load：首次加载在 ViewModel init 里做且幂等，
    // 否则每次返回详情页都会重建 PagingData → 列表重载 + 回到顶部。
    LaunchedEffect(Unit) {
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
        initialFirstVisibleItemIndex = viewModel.scrollState.firstVisibleItemIndex(),
        initialFirstVisibleItemScrollOffset = viewModel.scrollState.firstVisibleItemScrollOffset(),
        onScrollPositionChange = viewModel.scrollState::save,
    )
}
