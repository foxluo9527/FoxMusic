package com.fox.music.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.MusicActionBottomSheet
import com.fox.music.core.ui.component.PlaylistPickerBottomSheet
import com.fox.music.core.ui.component.ReportBottomSheet
import com.fox.music.core.model.music.Music

@Composable
fun MusicActionsHost(
    onArtistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    viewModel: MusicActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MusicActionsEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is MusicActionsEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                MusicActionsEffect.RequestCreatePlaylist -> onCreatePlaylist()
            }
        }
    }

    val music = state.actionMusic
    if (state.showActionSheet && music != null) {
        MusicActionBottomSheet(
            music = music,
            onDismiss = viewModel::dismissActionSheet,
            onPlayNext = viewModel::playNext,
            onAddToPlaylist = viewModel::showAddToPlaylistForCurrentMusic,
            onDownload = viewModel::downloadCurrentMusic,
            onReport = viewModel::showReportSheet,
            onArtistClick = viewModel::onArtistClick,
        )
    }

    if (state.showPlaylistPicker) {
        PlaylistPickerBottomSheet(
            playlists = state.playlists,
            isLoading = state.isPlaylistsLoading,
            onDismiss = viewModel::dismissPlaylistPicker,
            onPlaylistSelected = viewModel::addTracksToPlaylist,
            onCreatePlaylist = viewModel::requestCreatePlaylist,
        )
    }

    if (state.showReportSheet && music != null) {
        ReportBottomSheet(
            musicTitle = music.title,
            isSubmitting = state.isReportSubmitting,
            onDismiss = viewModel::dismissReportSheet,
            onSubmit = viewModel::submitReport,
        )
    }
}

fun MusicActionsViewModel.createMusicMoreClick(): (Music) -> Unit = { music ->
    showMusicActions(music)
}
