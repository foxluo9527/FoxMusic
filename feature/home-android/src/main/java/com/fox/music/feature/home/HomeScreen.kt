package com.fox.music.feature.home

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource

/** 首页 Android 外壳：hiltViewModel() + effect 消费，界面本体在 :feature:home 的 [HomeContent]。 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: HomeViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit = { _, _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onPlaylistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlaylistCategoryClick: () -> Unit = {},
    onAlbumCategoryClick: () -> Unit = {},
    onRecommendClick: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    effect.musicList,
                    HOME_ROUTE,
                    null,
                )
                is HomeEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlistId)
                is HomeEffect.NavigateToAlbum -> onAlbumClick(effect.albumId)
                is HomeEffect.NavigateToSearch -> onSearchClick()
                is HomeEffect.NavigateToPlaylistCategory -> onPlaylistCategoryClick()
                is HomeEffect.NavigateToAlbumCategory -> onAlbumCategoryClick()
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::sendIntent,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
        onMusicClick = viewModel::onMusicClick,
        onSearchClick = viewModel::onSearchClick,
        onPlaylistMoreClick = viewModel::onPlaylistMoreClick,
        onAlbumMoreClick = viewModel::onAlbumMoreClick,
        onRecommendClick = onRecommendClick,
        onMusicMoreClick = onMusicMoreClick,
        updateMusicList = updateMusicList,
        onRecommendedMusicChanged = viewModel::updateCurrentMusicList,
    )
}
