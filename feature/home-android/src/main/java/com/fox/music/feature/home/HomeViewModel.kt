package com.fox.music.feature.home

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlayHistoryUseCase
import com.fox.music.core.model.music.Music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val getPlayHistoryUseCase: GetPlayHistoryUseCase,
    private val searchRepository: SearchRepository,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var currentMusicList: List<Music> = emptyList()
    private var currentUserId: Long? = null

    init {
        viewModelScope.launch {
            sendIntent(HomeIntent.Load)
            userPreferencesRepository.userPreferences.collect { prefs ->
                if (prefs.userId != currentUserId) {
                    currentUserId = prefs.userId
                    sendIntent(HomeIntent.Refresh)
                }
            }
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
        val fromRecent = uiState.value.recentPlays.map { it.music }
        val playList = if (fromRecent.any { it.id == music.id }) {
            fromRecent.ifEmpty { list }
        } else {
            list
        }
        sendEffect(HomeEffect.NavigateToMusic(music, playList))
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
                is Result.Success -> updateState { copy(hotKeywords = result.data) }
                else -> {}
            }

            when (val result = getPlayHistoryUseCase(page = 1, limit = 12)) {
                is Result.Success -> updateState { copy(recentPlays = result.data.list) }
                else -> {}
            }

            when (val result = playlistRepository.getRecommendedPlaylists(page = 1, limit = 8)) {
                is Result.Success -> updateState { copy(recommendedPlaylists = result.data.list) }
                is Result.Error -> updateState { copy(error = result.message) }
                else -> {}
            }

            updateState {
                copy(
                    recommendedMusic = getMusicListUseCase.getPagingSource(
                        limit = 5,
                        sort = "recommend",
                    ).flow.cachedIn(viewModelScope),
                )
            }

            when (val result = albumRepository.getAlbumList(page = 1, limit = 8, sort = "hot")) {
                is Result.Success -> updateState { copy(recommendedAlbums = result.data.list) }
                else -> {}
            }

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }
}
