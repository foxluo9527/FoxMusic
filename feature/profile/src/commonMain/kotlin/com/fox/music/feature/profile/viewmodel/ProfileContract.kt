package com.fox.music.feature.profile.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ListeningStats
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.user.User

data class ProfileState(
    val user: User? = null,
    val playlists: List<Playlist> = emptyList(),
    val favoriteMusicTotal: Int = 0,
    val downloadedMusicTotal: Int = 0,
    val favoritePlaylists: List<Playlist> = emptyList(),
    val favoritePlaylistTotal: Int = 0,
    val favoriteAlbums: List<Album> = emptyList(),
    val favoriteAlbumTotal: Int = 0,
    val favoriteArtists: List<Artist> = emptyList(),
    val favoriteArtistTotal: Int = 0,
    val playHistoryTotal: Int = 0,
    val listeningStats: ListeningStats? = null,
    val unreadNotificationCount: Int = 0,
    val isLoading: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface ProfileIntent : UiIntent {
    data object Load : ProfileIntent
    data object RefreshListeningSnapshot : ProfileIntent
    data class OnPlaylistClick(val playlist: Playlist) : ProfileIntent
    data object OnFavoriteTracksClick : ProfileIntent
    data class CreatePlaylist(val title: String) : ProfileIntent
    data class ImportPlaylist(val url: String) : ProfileIntent
    data object OnSettingsClick : ProfileIntent
    data class PlayAllPlaylist(val playlistId: Long) : ProfileIntent
    data class DeletePlaylist(val playlistId: Long) : ProfileIntent
}

sealed interface ProfileEffect : UiEffect {
    data object NavigateToLogin : ProfileEffect
    data class NavigateToPlaylist(val playlistId: Long) : ProfileEffect
    data object NavigateToFavoriteTracks : ProfileEffect
    data object NavigateToCreatePlaylist : ProfileEffect
    data class ShowMessage(val message: String) : ProfileEffect
    data object NavigateToSettings : ProfileEffect
    data class PlayAllPlaylistTracks(
        val playlistId: Long,
        val musicList: List<Music>,
        val title: String,
        val coverUrl: String?,
    ) : ProfileEffect
}
