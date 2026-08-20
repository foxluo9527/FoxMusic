package com.fox.music.feature.search

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.usecase.ClearSearchHistoryUseCase
import com.fox.music.core.domain.usecase.GetHotKeywordsUseCase
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
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
import com.fox.music.core.model.music.SearchSuggest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val suggestions: List<SearchSuggest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val isSearchFocused: Boolean = false,
) : UiState {
    /** 聚焦或尚未搜索时展示推荐/建议面板；失焦且已搜索时展示结果 */
    val showRecommendPanel: Boolean
        get() = isSearchFocused || !hasSearched
}

sealed interface SearchIntent : UiIntent {
    data class QueryChange(val query: String) : SearchIntent
    data object Search : SearchIntent
    data class SelectHistory(val keyword: String) : SearchIntent
    data class SelectHotKeyword(val keyword: String) : SearchIntent
    data class SelectSuggest(val suggest: SearchSuggest) : SearchIntent
    data class FocusChanged(val focused: Boolean) : SearchIntent
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
    /** 请求 UI 清除搜索框焦点（收起软键盘） */
    data object ClearSearchFocus : SearchEffect
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
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
    private val searchRepository: SearchRepository,
) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {

    private var currentMusicList: List<Music> = emptyList()
    private var hotKeywordPage: Int = 0
    private var suggestJob: Job? = null

    /** 当前搜索关键词下，按平台缓存的单曲 Paging（切换平台时复用，避免重复打接口） */
    private var cachedMusicKeyword: String? = null
    private val musicPagingByPlatform =
        mutableMapOf<String, Flow<PagingData<Music>>>()
    private var cachedArtistResults: Flow<PagingData<Artist>>? = null
    private var cachedAlbumResults: Flow<PagingData<Album>>? = null

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
            is SearchIntent.QueryChange -> {
                updateState { copy(query = intent.query) }
                scheduleSuggest(intent.query)
            }
            SearchIntent.Search -> {
                updateState { copy(suggestions = emptyList(), isSearchFocused = false) }
                performSearch()
            }
            is SearchIntent.SelectHistory -> {
                updateState {
                    copy(
                        query = intent.keyword,
                        suggestions = emptyList(),
                        isSearchFocused = false,
                    )
                }
                performSearch(keyword = intent.keyword)
                sendEffect(SearchEffect.ClearSearchFocus)
            }
            is SearchIntent.SelectHotKeyword -> {
                updateState {
                    copy(
                        query = intent.keyword,
                        suggestions = emptyList(),
                        isSearchFocused = false,
                    )
                }
                performSearch(keyword = intent.keyword)
                sendEffect(SearchEffect.ClearSearchFocus)
            }
            is SearchIntent.SelectSuggest -> selectSuggest(intent.suggest)
            is SearchIntent.FocusChanged -> {
                updateState { copy(isSearchFocused = intent.focused) }
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

    private fun selectSuggest(suggest: SearchSuggest) {
        val type = suggest.type.lowercase()
        when {
            type == "artist" && suggest.id > 0 -> {
                updateState { copy(isSearchFocused = false, suggestions = emptyList()) }
                sendEffect(SearchEffect.ClearSearchFocus)
                sendEffect(SearchEffect.NavigateToArtist(suggest.id))
            }
            type == "album" && suggest.id > 0 -> {
                updateState { copy(isSearchFocused = false, suggestions = emptyList()) }
                sendEffect(SearchEffect.ClearSearchFocus)
                sendEffect(
                    SearchEffect.NavigateToAlbum(
                        Album(
                            id = suggest.id,
                            title = suggest.text,
                            coverImage = suggest.cover,
                        )
                    )
                )
            }
            (type == "music" || type.isBlank()) && suggest.id > 0 -> {
                viewModelScope.launch {
                    getMusicDetailUseCase(suggest.id)
                        .onSuccess { detail ->
                            updateState {
                                copy(isSearchFocused = false, suggestions = emptyList())
                            }
                            sendEffect(SearchEffect.ClearSearchFocus)
                            sendEffect(
                                SearchEffect.NavigateToMusic(
                                    music = detail.music,
                                    musicList = listOf(detail.music),
                                )
                            )
                        }
                        .onError { _, _ ->
                            updateState {
                                copy(
                                    query = suggest.text,
                                    suggestions = emptyList(),
                                    isSearchFocused = false,
                                )
                            }
                            performSearch(keyword = suggest.text)
                            sendEffect(SearchEffect.ClearSearchFocus)
                        }
                }
            }
            else -> {
                updateState {
                    copy(
                        query = suggest.text,
                        suggestions = emptyList(),
                        isSearchFocused = false,
                    )
                }
                performSearch(keyword = suggest.text)
                sendEffect(SearchEffect.ClearSearchFocus)
            }
        }
    }

    private fun performSearch(
        keyword: String? = null,
        platform: SearchMusicPlatform? = null
    ) {
        val q = keyword?.trim() ?: currentState.query.trim()
        if (q.isEmpty()) return
        val selectedPlatform = platform ?: currentState.selectedMusicPlatform
        val platformKey = selectedPlatform.apiValue.orEmpty()

        viewModelScope.launch {
            updateState {
                copy(
                    hasSearched = true,
                    isSearchFocused = false,
                    selectedTab = SearchResultTab.MUSIC,
                    selectedMusicPlatform = selectedPlatform,
                )
            }
            saveSearchHistoryUseCase(q)

            if (cachedMusicKeyword != q) {
                cachedMusicKeyword = q
                musicPagingByPlatform.clear()
                cachedArtistResults = null
                cachedAlbumResults = null
            }

            val musicFlow = musicPagingByPlatform.getOrPut(platformKey) {
                getSearchMusicPagingUseCase(
                    keyword = q,
                    platform = selectedPlatform.apiValue,
                ).cachedIn(viewModelScope)
            }
            val artistFlow = cachedArtistResults ?: getSearchArtistPagingUseCase(q)
                .cachedIn(viewModelScope)
                .also { cachedArtistResults = it }
            val albumFlow = cachedAlbumResults ?: getSearchAlbumPagingUseCase(q)
                .cachedIn(viewModelScope)
                .also { cachedAlbumResults = it }

            updateState {
                copy(
                    musicResults = musicFlow,
                    artistResults = artistFlow,
                    albumResults = albumFlow,
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

    private fun scheduleSuggest(query: String) {
        suggestJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            updateState { copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            searchRepository.getSuggest(trimmed, limit = 8)
                .onSuccess { list -> updateState { copy(suggestions = list) } }
                .onError { _, _ -> updateState { copy(suggestions = emptyList()) } }
        }
    }

    companion object {
        const val HISTORY_COLLAPSED_MAX = 6
        const val HISTORY_COLLAPSED_ROWS = 3
        const val HOT_KEYWORD_PAGE_SIZE = 8
        const val HOT_KEYWORD_MAX_ROWS = 4
    }
}
