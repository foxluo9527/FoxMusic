package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.feature.profile.viewmodel.ProfileEffect
import com.fox.music.feature.profile.viewmodel.ProfileIntent
import com.fox.music.feature.profile.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    isLogin: Boolean,
    onLogin: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onFavoritePlaylistClick: (Playlist) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onFavoriteTracksClick: () -> Unit = {},
    onPlayHistoryClick: () -> Unit = {},
    onCreatePlaylistClick: () -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUserHomeClick: () -> Unit = {},
    onDownloadManagerClick: () -> Unit = {},
    onPlayAllPlaylist: (Long, List<Music>, String, String?) -> Unit = { _, _, _, _ -> },
    onEditPlaylistClick: (Long) -> Unit = {},
    onMusicClick: (Music, List<Music>) -> Unit = { _, _ -> },
    refreshKey: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(isLogin) {
        if (isLogin) {
            viewModel.sendIntent(ProfileIntent.Load)
        }
    }

    LaunchedEffect(refreshKey) {
        if (isLogin && refreshKey > 0) {
            viewModel.refresh()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isLogin) {
        if (!isLogin) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.sendIntent(ProfileIntent.RefreshListeningSnapshot)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ProfileEffect.NavigateToLogin -> onLogin()
                is ProfileEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlistId)
                ProfileEffect.NavigateToFavoriteTracks -> onFavoriteTracksClick()
                ProfileEffect.NavigateToCreatePlaylist -> onCreatePlaylistClick()
                ProfileEffect.NavigateToSettings -> onSettingsClick()
                is ProfileEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is ProfileEffect.PlayAllPlaylistTracks -> {
                    onPlayAllPlaylist(
                        effect.playlistId,
                        effect.musicList,
                        effect.title,
                        effect.coverUrl,
                    )
                }
            }
        }
    }

    ProfileContent(
        state = state,
        onIntent = viewModel::sendIntent,
        isLogin = isLogin,
        modifier = modifier,
        onLogin = onLogin,
        onFavoritePlaylistClick = onFavoritePlaylistClick,
        onAlbumClick = onAlbumClick,
        onPlayHistoryClick = onPlayHistoryClick,
        onCreatePlaylistClick = onCreatePlaylistClick,
        onArtistClick = onArtistClick,
        onSettingsClick = onSettingsClick,
        onUserHomeClick = onUserHomeClick,
        onDownloadManagerClick = onDownloadManagerClick,
        onEditPlaylistClick = onEditPlaylistClick,
        onCreatePlaylistFromVm = { viewModel.onCreatePlaylistClick() },
    )
}
