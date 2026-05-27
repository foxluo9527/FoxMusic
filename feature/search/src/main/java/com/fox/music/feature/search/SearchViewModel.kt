package com.fox.music.feature.search

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.ClearSearchHistoryUseCase
import com.fox.music.core.domain.usecase.GetHotKeywordsUseCase
import com.fox.music.core.domain.usecase.GetSearchAlbumPagingUseCase
import com.fox.music.core.domain.usecase.GetSearchArtistPagingUseCase
import com.fox.music.core.domain.usecase.GetSearchHistoryUseCase
import com.fox.music.core.domain.usecase.GetSearchMusicPagingUseCase
import com.fox.music.core.domain.usecase.SaveSearchHistoryUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchResultTab {
    MUSIC,
    ARTIST,
    ALBUM,
}

data class SearchState(
    val query: String = "",
    val selectedTab: SearchResultTab = SearchResultTab.MUSIC,
    val musicResults: Flow<PagingData<Music>> = flowOf(PagingData.empty()),
    val artistResults: Flow<PagingData<Artist>> = flowOf(PagingData.empty()),
    val albumResults: Flow<PagingData<Album>> = flowOf(PagingData.empty()),
    val searchHistory: List<SearchHistory> = emptyList(),
    val hotKeywords: List<HotKeyword> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
) : UiState

sealed interface SearchIntent : UiIntent {
    data class QueryChange(val query: String) : SearchIntent
    data object Search : SearchIntent
    data class SelectHistory(val keyword: String) : SearchIntent
    data class SelectHotKeyword(val keyword: String) : SearchIntent
    data class SelectTab(val tab: SearchResultTab) : SearchIntent
    data object ClearHistory : SearchIntent
}

sealed interface SearchEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : SearchEffect
    data class NavigateToArtist(val artistId: Long) : SearchEffect
    data class NavigateToAlbum(val album: Album) : SearchEffect
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSearchMusicPagingUseCase: GetSearchMusicPagingUseCase,
    private val getSearchArtistPagingUseCase: GetSearchArtistPagingUseCase,
    private val getSearchAlbumPagingUseCase: GetSearchAlbumPagingUseCase,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val saveSearchHistoryUseCase: SaveSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    private val getHotKeywordsUseCase: GetHotKeywordsUseCase,
) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {

    init {
        viewModelScope.launch {
            getSearchHistoryUseCase(20).catch { }.collect { history ->
                updateState { copy(searchHistory = history) }
            }
        }
        viewModelScope.launch {
            getHotKeywordsUseCase(limit = 10).onSuccess { keywords ->
                updateState { copy(hotKeywords = keywords) }
            }
        }
    }

    override fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChange -> updateState { copy(query = intent.query) }
            SearchIntent.Search -> performSearch()
            is SearchIntent.SelectHistory -> {
                updateState { copy(query = intent.keyword) }
                performSearch(keyword = intent.keyword)
            }
            is SearchIntent.SelectHotKeyword -> {
                updateState { copy(query = intent.keyword) }
                performSearch(keyword = intent.keyword)
            }
            is SearchIntent.SelectTab -> updateState { copy(selectedTab = intent.tab) }
            SearchIntent.ClearHistory -> clearHistory()
        }
    }

    fun onMusicClick(music: Music) {
        sendEffect(SearchEffect.NavigateToMusic(music))
    }

    fun onArtistClick(artistId: Long) {
        sendEffect(SearchEffect.NavigateToArtist(artistId))
    }

    fun onAlbumClick(album: Album) {
        sendEffect(SearchEffect.NavigateToAlbum(album))
    }

    private fun performSearch(keyword: String? = null) {
        val q = keyword?.trim() ?: currentState.query.trim()
        if (q.isEmpty()) return

        viewModelScope.launch {
            updateState { copy(hasSearched = true, selectedTab = SearchResultTab.MUSIC) }
            saveSearchHistoryUseCase(q)

            updateState {
                copy(
                    musicResults = getSearchMusicPagingUseCase(q).cachedIn(viewModelScope),
                    artistResults = getSearchArtistPagingUseCase(q).cachedIn(viewModelScope),
                    albumResults = getSearchAlbumPagingUseCase(q).cachedIn(viewModelScope),
                )
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            clearSearchHistoryUseCase().onSuccess {
                updateState { copy(searchHistory = emptyList()) }
            }
        }
    }
}
