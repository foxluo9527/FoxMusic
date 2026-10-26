package com.fox.music.feature.playlist.ui.component

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailEffect
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailState
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailViewModel
import com.fox.music.feature.playlist.viewmodel.toPlaybackSource

@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit = { _, _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onPlaylistDeleted: () -> Unit = {},
    onEditPlaylist: (Long) -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onAddSelectedToQueue: (List<Music>) -> Unit = {},
    onAddSelectedToPlaylist: (List<Music>) -> Unit = {},
    onDownloadSelected: (List<Music>) -> Unit = {},
    onSelectionModeChanged: (Boolean) -> Unit = {},
    onOpenImportDetail: () -> Unit = {},
    currentMusicId: Long? = null,
    /** 整份列表（为你精选/新歌速递）加载成功：同步首页/发现页数据与正在播放的队列 */
    onTopListLoaded: (String, List<Music>) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.tracks.collectAsLazyPagingItems()
    val context = LocalContext.current
    val playlistKey by viewModel.playlistKey.collectAsState()

    LaunchedEffect(state.isSelectionMode) {
        onSelectionModeChanged(state.isSelectionMode)
    }
    DisposableEffect(Unit) {
        onDispose { onSelectionModeChanged(false) }
    }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.sendIntent(PlaylistDetailIntent.ExitSelectionMode)
    }

    // 收集当前加载的歌曲列表
    LaunchedEffect(pagingItems.itemSnapshotList.items) {
        viewModel.updateCurrentTrackList(pagingItems.itemSnapshotList.items)
    }
    LaunchedEffect(pagingItems.itemSnapshotList.items, playlistKey) {
        if (playlistKey.isNotEmpty()) {
            updateMusicList(pagingItems.itemSnapshotList.items, playlistKey)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PlaylistDetailEffect.NavigateToMusic -> {
                    if (playlistKey.isNotEmpty()) {
                        onMusicClick(
                            effect.music,
                            effect.musicList,
                            playlistKey,
                            state.headerInfo?.toPlaybackSource(),
                        )
                    }
                }

                is PlaylistDetailEffect.PlayAllTracks -> {
                    if (playlistKey.isNotEmpty() && effect.musicList.isNotEmpty()) {
                        onMusicClick(
                            effect.musicList.first(),
                            effect.musicList,
                            playlistKey,
                            state.headerInfo?.toPlaybackSource(),
                        )
                    }
                }

                is PlaylistDetailEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                PlaylistDetailEffect.NavigateBack -> {
                    onPlaylistDeleted()
                    onBack()
                }

                is PlaylistDetailEffect.AddSelectedToQueue -> {
                    onAddSelectedToQueue(effect.musics)
                }

                is PlaylistDetailEffect.AddSelectedToPlaylist -> {
                    onAddSelectedToPlaylist(effect.musics)
                }

                is PlaylistDetailEffect.DownloadSelected -> {
                    onDownloadSelected(effect.musics)
                }

                PlaylistDetailEffect.RefreshTracks -> {
                    pagingItems.refresh()
                }

                is PlaylistDetailEffect.TopListLoaded -> {
                    onTopListLoaded(effect.sort, effect.list)
                }
            }
        }
    }

    PlaylistDetailContent(
        state = state,
        pagingItems = pagingItems,
        playlistKey = playlistKey,
        playlistId = viewModel.playlistId,
        canManagePlaylist = viewModel.canManagePlaylist,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        onMusicClick = { music -> viewModel.onMusicClick(music, pagingItems.itemSnapshotList.items) },
        onPlayAll = { viewModel.sendIntent(PlaylistDetailIntent.PlayAll) },
        onToggleFavorite = { viewModel.sendIntent(PlaylistDetailIntent.ToggleFavorite) },
        onMusicMoreClick = onMusicMoreClick,
        onArtistClick = onArtistClick,
        onEnterSelectionMode = { viewModel.sendIntent(PlaylistDetailIntent.EnterSelectionMode(it)) },
        onToggleSelection = { viewModel.sendIntent(PlaylistDetailIntent.ToggleSelection(it)) },
        onBack = onBack,
        onExitSelectionMode = { viewModel.sendIntent(PlaylistDetailIntent.ExitSelectionMode) },
        onSelectAll = { viewModel.sendIntent(PlaylistDetailIntent.SelectAll) },
        onRefresh = { viewModel.sendIntent(PlaylistDetailIntent.Refresh) },
        onAddSelectedToQueue = { viewModel.sendIntent(PlaylistDetailIntent.AddSelectedToQueue) },
        onRemoveSelectedFromPlaylist = { viewModel.sendIntent(PlaylistDetailIntent.RemoveSelectedFromPlaylist) },
        onAddSelectedToPlaylist = { viewModel.sendIntent(PlaylistDetailIntent.AddSelectedToPlaylist) },
        onDownloadSelected = { viewModel.sendIntent(PlaylistDetailIntent.DownloadSelected) },
        onPauseImport = { viewModel.sendIntent(PlaylistDetailIntent.PauseImport) },
        onResumeImport = { viewModel.sendIntent(PlaylistDetailIntent.ResumeImport) },
        onRetryImport = { viewModel.sendIntent(PlaylistDetailIntent.RetryImport) },
        onCancelFailedImport = { viewModel.sendIntent(PlaylistDetailIntent.CancelFailedImport) },
        onOpenImportDetail = onOpenImportDetail,
        onEditPlaylist = onEditPlaylist,
        onDeletePlaylist = { viewModel.sendIntent(PlaylistDetailIntent.DeletePlaylist) },
        currentMusicId = currentMusicId,
        modifier = modifier,
    )
}
