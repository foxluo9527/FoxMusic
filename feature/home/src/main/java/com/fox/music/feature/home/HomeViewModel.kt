package com.fox.music.feature.home

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val hotKeywords: List<HotKeyword> = emptyList(),
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val searchRepository: SearchRepository,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
): MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var currentMusicList: List<Music> = emptyList()

    init {
        viewModelScope.launch {
            sendIntent(HomeIntent.Load)
        }
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> loadContent(isRefresh = false)
            HomeIntent.Refresh -> loadContent(isRefresh = true)
            is HomeIntent.OnPlaylistClick -> {
                sendEffect(HomeEffect.NavigateToPlaylist(intent.playlist.id))
            }
            is HomeIntent.OnAlbumClick -> {
                sendEffect(HomeEffect.NavigateToAlbum(intent.album.id))
            }
        }
    }

    fun updateCurrentMusicList(list: List<Music>) {
        currentMusicList = list
    }

    fun onMusicClick(music: Music) {
        val list = currentMusicList.takeIf { it.isNotEmpty() } ?: listOf(music)
        sendEffect(HomeEffect.NavigateToMusic(music, list))
    }

    fun onSearchClick() {
        sendEffect(HomeEffect.NavigateToSearch)
    }

    fun onPlaylistMoreClick() {
        sendEffect(HomeEffect.NavigateToPlaylistCategory)
    }

    fun onAlbumMoreClick() {
        sendEffect(HomeEffect.NavigateToAlbumCategory)
    }

    private fun loadContent(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                updateState { copy(isRefreshing = true, error = null) }
            } else {
                updateState { copy(isLoading = true, error = null) }
            }

            when (val result = searchRepository.getHotKeywords(limit = 10)) {
                is Result.Success -> {
                    updateState { copy(hotKeywords = result.data) }
                }
                is Result.Error -> {}
                else -> {}
            }

            when (val result = playlistRepository.getRecommendedPlaylists(page = 1, limit = 8)) {
                is Result.Success -> {
                    updateState { copy(recommendedPlaylists = result.data.list) }
                }
                is Result.Error -> {
                    updateState { copy(error = result.message) }
                }
                else -> {}
            }

            updateState {
                copy(
                    recommendedMusic = getMusicListUseCase.getPagingSource(
                        limit = 5,
                        sort = "recommend"
                    ).flow.cachedIn(viewModelScope)
                )
            }

            when (val result = albumRepository.getAlbumList(page = 1, limit = 8, sort = "hot")) {
                is Result.Success -> {
                    updateState { copy(recommendedAlbums = result.data.list) }
                }
                is Result.Error -> {}
                else -> {}
            }

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }
}
