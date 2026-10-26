package com.fox.music.feature.discover

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind

/**
 * 发现页 Android 外壳：只做 hiltViewModel() + effect 消费，界面本体在 :feature:discover 的
 * [DiscoverContent]（commonMain）。
 */
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit = { _, _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onRankClick: (Long) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNewMusicMore: () -> Unit = {},
    onHotArtistMore: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DiscoverEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    state.newMusic,
                    DISCOVER_ROUTE,
                    // 新歌速递入队带上来源，播放列表弹窗可展示并跳回新歌详情页
                    PlaybackPlaylistSource(
                        kind = PlaybackSourceKind.NEW_MUSIC,
                        id = DetailType.NEW_MUSIC_ID,
                        title = "新歌速递",
                    ),
                )
                is DiscoverEffect.NavigateToRank -> onRankClick(effect.rankId)
                is DiscoverEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                DiscoverEffect.NavigateToSearch -> onSearchClick()
            }
        }
    }

    LaunchedEffect(state.newMusic) {
        if (state.newMusic.isNotEmpty()) {
            updateMusicList(state.newMusic, DISCOVER_ROUTE)
        }
    }

    DiscoverContent(
        state = state,
        onIntent = viewModel::sendIntent,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
        onSearchClick = viewModel::onSearchClick,
        onNewMusicMore = onNewMusicMore,
        onHotArtistMore = onHotArtistMore,
        onMusicMoreClick = onMusicMoreClick,
        onOpenDrawer = onOpenDrawer,
    )
}
