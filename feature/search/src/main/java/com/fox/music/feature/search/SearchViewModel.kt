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

enum class SearchMusicPlatform(val apiValue: String?, val label: String) {
    FOX(null, "平台曲库"),
    QQ("qq", "QQ 音乐"),
    NETEASE("netease", "网易云")
}

data class SearchState(
    val query: String = "",
    val selectedTab: SearchResultTab = SearchResultTab.MUSIC,
    val selectedMusicPlatform: SearchMusicPlatform = SearchMusicPlatform.FOX,
    val musicResults: Flow<PagingData<Music>> = flowOf(PagingData.empty()),
    val artistResults: Flow<PagingData<Artist>> = flowOf(PagingData.empty()),
    val albumResults: Flow<PagingData<Album>> = flowOf(PagingData.empty()),
    val searchHistory: List<SearchHistory> = emptyList(),
    val isHistoryExpanded: Boolean = false,
    val hotKeywords: List<HotKeyword> = emptyList(),
    val hotKeywordsHasMore: Boolean = false,
    val isLoadingHotKeywords: Boolean = false,
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
    data class SelectMusicPlatform(val platform: SearchMusicPlatform) : SearchIntent
    data object ToggleHistoryExpanded : SearchIntent
    data object ClearHistory : SearchIntent
    data object RefreshHotKeywords : SearchIntent
}

sealed interface SearchEffect : UiEffect {
    data class NavigateToMusic(val music: Music, val musicList: List<Music>) : SearchEffect
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

    private var currentMusicList: List<Music> = emptyList()
    private var hotKeywordPage: Int = 0

    init {
        viewModelScope.launch {
            getSearchHistoryUseCase(20).catch { }.collect { history ->
                val deduped = history.distinctBy { it.keyword }
                updateState {
                    val expanded = isHistoryExpanded && deduped.size > HISTORY_COLLAPSED_MAX
                    copy(
                        searchHistory = deduped,
                        isHistoryExpanded = expanded,
                    )
                }
            }
        }
        viewModelScope.launch {
            loadHotKeywords(page = 0)
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
            is SearchIntent.SelectMusicPlatform -> {
                updateState { copy(selectedMusicPlatform = intent.platform) }
                if (currentState.hasSearched && currentState.query.isNotBlank()) {
                    performSearch(currentState.query, intent.platform)
                }
            }
            SearchIntent.ToggleHistoryExpanded -> {
                updateState { copy(isHistoryExpanded = !isHistoryExpanded) }
            }
            SearchIntent.ClearHistory -> clearHistory()
            SearchIntent.RefreshHotKeywords -> refreshHotKeywords()
        }
    }

    fun updateCurrentMusicList(list: List<Music>) {
        currentMusicList = list
    }

    fun onMusicClick(music: Music) {
        val list = currentMusicList.takeIf { it.isNotEmpty() } ?: listOf(music)
        sendEffect(SearchEffect.NavigateToMusic(music, list))
    }

    fun onArtistClick(artistId: Long) {
        sendEffect(SearchEffect.NavigateToArtist(artistId))
    }

    fun onAlbumClick(album: Album) {
        sendEffect(SearchEffect.NavigateToAlbum(album))
    }

    private fun performSearch(
        keyword: String? = null,
        platform: SearchMusicPlatform? = null
    ) {
        val q = keyword?.trim() ?: currentState.query.trim()
        if (q.isEmpty()) return
        val selectedPlatform = platform ?: currentState.selectedMusicPlatform

        viewModelScope.launch {
            updateState { copy(hasSearched = true, selectedTab = SearchResultTab.MUSIC) }
            saveSearchHistoryUseCase(q)

            updateState {
                copy(
                    musicResults = getSearchMusicPagingUseCase(
                        keyword = q,
                        platform = selectedPlatform.apiValue
                    ).cachedIn(viewModelScope),
                    artistResults = getSearchArtistPagingUseCase(q).cachedIn(viewModelScope),
                    albumResults = getSearchAlbumPagingUseCase(q).cachedIn(viewModelScope),
                )
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            clearSearchHistoryUseCase().onSuccess {
                updateState { copy(searchHistory = emptyList(), isHistoryExpanded = false) }
            }
        }
    }

    private fun refreshHotKeywords() {
        val state = currentState
        if (!state.hotKeywordsHasMore || state.isLoadingHotKeywords) return
        viewModelScope.launch {
            loadHotKeywords(page = hotKeywordPage + 1)
        }
    }

    private suspend fun loadHotKeywords(page: Int) {
        updateState { copy(isLoadingHotKeywords = true) }
        val limit = (page + 1) * HOT_KEYWORD_PAGE_SIZE
        getHotKeywordsUseCase(limit = limit)
            .onSuccess { keywords ->
                val distinct = keywords.distinctBy { it.keyword }
                val start = page * HOT_KEYWORD_PAGE_SIZE
                val pageItems = distinct.drop(start).take(HOT_KEYWORD_PAGE_SIZE)
                val hasMore = when {
                    pageItems.isEmpty() -> false
                    page > 0 && pageItems.size < HOT_KEYWORD_PAGE_SIZE -> false
                    else -> distinct.size >= limit
                }
                if (pageItems.isNotEmpty()) {
                    hotKeywordPage = page
                }
                updateState {
                    copy(
                        hotKeywords = pageItems.ifEmpty { hotKeywords },
                        hotKeywordsHasMore = hasMore,
                        isLoadingHotKeywords = false,
                    )
                }
            }
            .onError { _, _ ->
                updateState { copy(isLoadingHotKeywords = false) }
            }
    }

    companion object {
        const val HISTORY_COLLAPSED_MAX = 6
        const val HISTORY_COLLAPSED_ROWS = 3
        const val HOT_KEYWORD_PAGE_SIZE = 8
        const val HOT_KEYWORD_MAX_ROWS = 4
    }
}
