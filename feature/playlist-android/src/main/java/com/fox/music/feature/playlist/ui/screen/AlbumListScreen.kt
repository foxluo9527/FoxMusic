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

    // 只收集 effect，不再发 Load：首次加载在 ViewModel init 里做且幂等，
    // 否则每次从详情页返回都会重建 PagingData → 列表重载 + 回到顶部。
    LaunchedEffect(Unit) {
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
        initialFirstVisibleItemIndex = viewModel.scrollState.firstVisibleItemIndex(),
        initialFirstVisibleItemScrollOffset = viewModel.scrollState.firstVisibleItemScrollOffset(),
        onScrollPositionChange = viewModel.scrollState::save,
    )
}
