package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.usecase.DeletePlaylistUseCase
import com.fox.music.core.domain.usecase.GetFavoritesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.user.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val user: User? = null,
    val playlists: List<Playlist> = emptyList(),
    val favoriteMusicTotal: Int = 0,
    val favoritePlaylists: List<Playlist> = emptyList(),
    val favoritePlaylistTotal: Int = 0,
    val favoriteAlbums: List<Album> = emptyList(),
    val favoriteAlbumTotal: Int = 0,
    val favoriteArtists: List<Artist> = emptyList(),
    val favoriteArtistTotal: Int = 0,
    val isLoading: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface ProfileIntent : UiIntent {
    data object Load : ProfileIntent
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
    ) : ProfileEffect
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val playlistRepository: PlaylistRepository,
    private val importRepository: ImportRepository,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    override fun handleIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Load -> load()
            is ProfileIntent.OnPlaylistClick -> {
                sendEffect(ProfileEffect.NavigateToPlaylist(intent.playlist.id))
            }
            ProfileIntent.OnFavoriteTracksClick -> {
                sendEffect(ProfileEffect.NavigateToFavoriteTracks)
            }
            is ProfileIntent.CreatePlaylist -> createPlaylist(intent.title)
            is ProfileIntent.ImportPlaylist -> importPlaylist(intent.url)
            ProfileIntent.OnSettingsClick -> sendEffect(ProfileEffect.NavigateToSettings)
            is ProfileIntent.PlayAllPlaylist -> playAllPlaylist(intent.playlistId)
            is ProfileIntent.DeletePlaylist -> deletePlaylist(intent.playlistId)
        }
    }

    private fun playAllPlaylist(playlistId: Long) {
        viewModelScope.launch {
            getPlaylistDetailUseCase(playlistId, page = 1, limit = 500)
                .onSuccess { detail ->
                    val tracks = detail.tracks.list
                    if (tracks.isEmpty()) {
                        sendEffect(ProfileEffect.ShowMessage("暂无歌曲"))
                    } else {
                        sendEffect(ProfileEffect.PlayAllPlaylistTracks(playlistId, tracks))
                    }
                }
                .onError { _, msg ->
                    sendEffect(ProfileEffect.ShowMessage(msg ?: "加载歌曲失败"))
                }
        }
    }

    private fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
                .onSuccess {
                    getPlaylistListUseCase()
                        .onSuccess { playlists -> updateState { copy(playlists = playlists) } }
                    sendEffect(ProfileEffect.ShowMessage("歌单已删除"))
                }
                .onError { _, msg ->
                    sendEffect(ProfileEffect.ShowMessage(msg ?: "删除失败"))
                }
        }
    }

    fun refresh() {
        load()
    }

    fun onCreatePlaylistClick() {
        sendEffect(ProfileEffect.NavigateToCreatePlaylist)
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            getProfileUseCase()
                .onSuccess { user -> updateState { copy(user = user) } }
                .onError { _, msg -> updateState { copy(error = msg ?: "Failed") } }

            getPlaylistListUseCase()
                .onSuccess { playlists -> updateState { copy(playlists = playlists) } }

            getFavoritesUseCase.getMusics(limit = 50)
                .onSuccess { data ->
                    updateState { copy(favoriteMusicTotal = data.total) }
                }

            getFavoritesUseCase.getPlaylists(limit = 50)
                .onSuccess { data ->
                    updateState {
                        copy(
                            favoritePlaylists = data.list,
                            favoritePlaylistTotal = data.total,
                        )
                    }
                }

            getFavoritesUseCase.getAlbums(limit = 50)
                .onSuccess { data ->
                    updateState {
                        copy(
                            favoriteAlbums = data.list,
                            favoriteAlbumTotal = data.total,
                        )
                    }
                }

            getFavoritesUseCase.getArtists(limit = 50)
                .onSuccess { data ->
                    updateState {
                        copy(
                            favoriteArtists = data.list,
                            favoriteArtistTotal = data.total,
                        )
                    }
                }

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
                tagIds = null,
            ).onSuccess { playlist ->
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
                        load()
                    }
                }.onError { _, msg ->
                    updateState { copy(isImporting = false) }
                    sendEffect(ProfileEffect.ShowMessage(msg ?: "导入失败"))
                }
        }
    }
}
