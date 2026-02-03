package com.fox.music.feature.home

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.Music
import com.fox.music.core.ui.components.MusicList

const val HOME_ROUTE = "home"

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: HomeViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>) -> Unit = {_, _ ->},
) {
    val state by viewModel.uiState.collectAsState()
    val pagingItems = state.recommendedMusic.collectAsLazyPagingItems()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    pagingItems.itemSnapshotList.items
                )
            }
        }
    }
    MusicList(modifier, sharedTransitionScope, animatedContentScope, pagingItems) {
        viewModel.onMusicClick(it)
    }
}
