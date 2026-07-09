package com.fox.music.car

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetHotKeywordsUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPlaylistsUseCase
import com.fox.music.core.domain.usecase.SearchMusicUseCase
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail
import com.fox.music.core.ui.component.CarModeTab
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class CarModeState(
    val selectedTab: CarModeTab = CarModeTab.Recommend,
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val recommendedSongs: List<Music> = emptyList(),
    val hotKeywords: List<HotKeyword> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Music> = emptyList(),
    val isSearching: Boolean = false,
    val playlistDetail: PlaylistDetail? = null,
    val isPlaylistLoading: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
) : UiState

sealed interface CarModeIntent : UiIntent {
    data object Load : CarModeIntent
    data class SelectTab(val tab: CarModeTab) : CarModeIntent
    data class SelectPlaylist(val playlist: Playlist) : CarModeIntent
    data object BackFromPlaylist : CarModeIntent
    data class PlaySong(val music: Music, val playlist: List<Music>) : CarModeIntent
    data class UpdateSearchQuery(val query: String) : CarModeIntent
    data class SubmitSearch(val query: String) : CarModeIntent
    data class SelectHotKeyword(val keyword: String) : CarModeIntent
}

sealed interface CarModeEffect : UiEffect

@HiltViewModel
class CarModeViewModel @Inject constructor(
    private val getRecommendedPlaylistsUseCase: GetRecommendedPlaylistsUseCase,
    private val getMusicListUseCase: GetMusicListUseCase,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val searchMusicUseCase: SearchMusicUseCase,
    private val getHotKeywordsUseCase: GetHotKeywordsUseCase,
    private val musicController: MusicController,
) : MviViewModel<CarModeState, CarModeIntent, CarModeEffect>(CarModeState()) {

    init {
        sendIntent(CarModeIntent.Load)
    }

    override fun handleIntent(intent: CarModeIntent) {
        when (intent) {
            CarModeIntent.Load -> loadRecommendContent()
            is CarModeIntent.SelectTab -> updateState {
                copy(selectedTab = intent.tab, playlistDetail = null, isPlaylistLoading = false)
            }
            is CarModeIntent.SelectPlaylist -> loadPlaylistDetail(intent.playlist)
            CarModeIntent.BackFromPlaylist -> updateState {
                copy(playlistDetail = null, isPlaylistLoading = false)
            }
            is CarModeIntent.PlaySong -> playSong(intent.music, intent.playlist)
            is CarModeIntent.UpdateSearchQuery -> updateState { copy(searchQuery = intent.query) }
            is CarModeIntent.SubmitSearch -> search(intent.query)
            is CarModeIntent.SelectHotKeyword -> {
                updateState { copy(searchQuery = intent.keyword, selectedTab = CarModeTab.Search) }
                search(intent.keyword)
            }
        }
    }

    private fun loadRecommendContent() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            val playlists = when (val result = getRecommendedPlaylistsUseCase(page = 1, limit = 12)) {
                is Result.Success -> result.data.list
                is Result.Error -> {
                    updateState { copy(error = result.message, isLoading = false) }
                    return@launch
                }
                else -> emptyList()
            }
            val songs = when (val result = getMusicListUseCase(page = 1, limit = 30, sort = "recommend")) {
                is Result.Success -> result.data.list
                else -> emptyList()
            }
            val hotKeywords = when (val result = getHotKeywordsUseCase(limit = 8)) {
                is Result.Success -> result.data
                else -> emptyList()
            }
            updateState {
                copy(
                    recommendedPlaylists = playlists,
                    recommendedSongs = songs,
                    hotKeywords = hotKeywords,
                    isLoading = false,
                    error = null,
                )
            }
        }
    }

    private fun loadPlaylistDetail(playlist: Playlist) {
        updateState {
            copy(
                playlistDetail = PlaylistDetail(playlist = playlist, tracks = PagedData()),
                isPlaylistLoading = true,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = getPlaylistDetailUseCase(playlist.id, page = 1, limit = 50)) {
                is Result.Success -> updateState {
                    copy(playlistDetail = result.data, isPlaylistLoading = false, error = null)
                }
                is Result.Error -> updateState {
                    copy(isPlaylistLoading = false, error = result.message)
                }
                else -> updateState { copy(isPlaylistLoading = false) }
            }
        }
    }

    private fun search(query: String) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            updateState { copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            updateState { copy(isSearching = true, searchQuery = keyword, error = null) }
            when (val result = searchMusicUseCase(keyword = keyword, page = 1, limit = 30)) {
                is Result.Success -> updateState {
                    copy(searchResults = result.data.list, isSearching = false)
                }
                is Result.Error -> updateState {
                    copy(isSearching = false, error = result.message)
                }
                else -> updateState { copy(isSearching = false) }
            }
        }
    }

    private fun playSong(music: Music, playlist: List<Music>) {
        val list = playlist.ifEmpty { listOf(music) }
        val index = list.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
        musicController.setPlaylist(list, startIndex = index, key = "car_mode")
        musicController.play()
    }
}
