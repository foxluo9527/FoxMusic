package com.fox.music.feature.home

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.model.music.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class HomeState(
    val hotKeywords: List<HotKeyword> = emptyList(),
    val recentPlays: List<PlayHistory> = emptyList(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val recommendedMusic: Flow<PagingData<Music>> = flowOf(),
    val recommendedAlbums: List<Album> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Refresh : HomeIntent
    data class OnPlaylistClick(val playlist: Playlist) : HomeIntent
    data class OnAlbumClick(val album: Album) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToMusic(val music: Music, val musicList: List<Music>) : HomeEffect
    data class NavigateToPlaylist(val playlistId: Long) : HomeEffect
    data class NavigateToAlbum(val albumId: Long) : HomeEffect
    data object NavigateToSearch : HomeEffect
    data object NavigateToPlaylistCategory : HomeEffect
    data object NavigateToAlbumCategory : HomeEffect
}
