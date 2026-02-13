package com.fox.music.feature.profile

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.usecase.GetFavoritesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.model.Favorite
import com.fox.music.core.model.FavoriteType
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val user: User? = null,
    val playlists: List<Playlist> = emptyList(),
    val favoriteTracks: List<Favorite> = emptyList(),
    val favoritePlaylists: List<Favorite> = emptyList(),
    val favoriteAlbums: List<Favorite> = emptyList(),
    val favoriteArtists: List<Favorite> = emptyList(),
    val isLoading: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null
) : UiState

sealed interface ProfileIntent : UiIntent {
    data object Load : ProfileIntent
    data class OnPlaylistClick(val playlist: Playlist) : ProfileIntent
    data class CreatePlaylist(val title: String) : ProfileIntent
    data class ImportPlaylist(val url: String) : ProfileIntent
}

sealed interface ProfileEffect : UiEffect {
    data object NavigateToLogin : ProfileEffect
    data class NavigateToPlaylist(val playlistId: Long) : ProfileEffect
    data object NavigateToCreatePlaylist : ProfileEffect
    data class ShowMessage(val message: String) : ProfileEffect
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val playlistRepository: PlaylistRepository,
    private val importRepository: ImportRepository
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    override fun handleIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Load -> load()
            is ProfileIntent.OnPlaylistClick -> {
                sendEffect(ProfileEffect.NavigateToPlaylist(intent.playlist.id))
            }
            is ProfileIntent.CreatePlaylist -> createPlaylist(intent.title)
            is ProfileIntent.ImportPlaylist -> importPlaylist(intent.url)
        }
    }

    fun onCreatePlaylistClick() {
        sendEffect(ProfileEffect.NavigateToCreatePlaylist)
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            // Load user profile
            getProfileUseCase()
                .onSuccess { user -> updateState { copy(user = user) } }
                .onError { _, msg -> updateState { copy(error = msg ?: "Failed") } }

            // Load user playlists
            getPlaylistListUseCase()
                .onSuccess { playlists -> updateState { copy(playlists = playlists) } }

            getFavoritesUseCase(type = FavoriteType.MUSIC, limit = 50)
                .onSuccess { data -> updateState { copy(favoriteTracks = data.list) } }

            // Load favorite playlists
            getFavoritesUseCase(type = FavoriteType.PLAYLIST, limit = 50)
                .onSuccess { data -> updateState { copy(favoritePlaylists = data.list) } }

            // Load favorite albums
            getFavoritesUseCase(type = FavoriteType.ALBUM, limit = 50)
                .onSuccess { data -> updateState { copy(favoriteAlbums = data.list) } }

            // Load favorite artists
            getFavoritesUseCase(type = FavoriteType.ARTIST, limit = 50)
                .onSuccess { data -> updateState { copy(favoriteArtists = data.list) } }

            updateState { copy(isLoading = false) }
        }
    }

    private fun createPlaylist(title: String) {
        viewModelScope.launch {
            updateState { copy(isCreatingPlaylist = true) }

            playlistRepository.createPlaylist(
                title = title,
                description = null,
                coverImage = null,
                isPublic = true,
                tagIds = null
            ).onSuccess { playlist ->
                // Reload playlists
                getPlaylistListUseCase()
                    .onSuccess { playlists -> updateState { copy(playlists = playlists) } }

                updateState { copy(isCreatingPlaylist = false) }
                sendEffect(ProfileEffect.ShowMessage("歌单创建成功"))
                sendEffect(ProfileEffect.NavigateToPlaylist(playlist.id))
            }.onError { _, msg ->
                updateState { copy(isCreatingPlaylist = false) }
                sendEffect(ProfileEffect.ShowMessage(msg ?: "创建失败"))
            }
        }
    }

    private fun importPlaylist(url: String) {
        viewModelScope.launch {
            updateState { copy(isImporting = true) }

            importRepository.importMusic(url)
                .onSuccess { response ->
                    updateState { copy(isImporting = false) }
                    if (response.isImporting) {
                        sendEffect(ProfileEffect.ShowMessage("正在导入歌单，请稍后..."))
                    } else {
                        sendEffect(ProfileEffect.ShowMessage("导入成功"))
                        // Reload playlists
                        load()
                    }
                }.onError { _, msg ->
                    updateState { copy(isImporting = false) }
                    sendEffect(ProfileEffect.ShowMessage(msg ?: "导入失败"))
                }
        }
    }
}
