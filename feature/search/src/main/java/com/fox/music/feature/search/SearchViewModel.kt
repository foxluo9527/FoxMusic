package com.fox.music.feature.search

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.ClearSearchHistoryUseCase
import com.fox.music.core.domain.usecase.GetHotKeywordsUseCase
import com.fox.music.core.domain.usecase.GetSearchHistoryUseCase
import com.fox.music.core.domain.usecase.SaveSearchHistoryUseCase
import com.fox.music.core.domain.usecase.SearchMusicUseCase
import com.fox.music.core.model.Music
import com.fox.music.core.model.SearchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val results: List<Music> = emptyList(),
    val searchHistory: List<SearchHistory> = emptyList(),
    val hotKeywords: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
) : UiState

sealed interface SearchIntent : UiIntent {
    data class QueryChange(val query: String) : SearchIntent
    data object Search : SearchIntent
    data class SelectHistory(val keyword: String) : SearchIntent
    data class SelectHotKeyword(val keyword: String) : SearchIntent
    data object ClearHistory : SearchIntent
}

sealed interface SearchEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : SearchEffect
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMusicUseCase: SearchMusicUseCase,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val saveSearchHistoryUseCase: SaveSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    private val getHotKeywordsUseCase: GetHotKeywordsUseCase
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
            SearchIntent.ClearHistory -> clearHistory()
        }
    }

    fun onMusicClick(music: Music) {
        sendEffect(SearchEffect.NavigateToMusic(music))
    }

    private fun performSearch(keyword: String? = null) {
        val q = keyword?.trim() ?: currentState.query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null, hasSearched = true) }
            saveSearchHistoryUseCase(q)
            searchMusicUseCase(keyword = q, limit = 30)
                .onSuccess { data ->
                    updateState {
                        copy(
                            results = data.list,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onError { _, message ->
                    updateState {
                        copy(
                            isLoading = false,
                            error = message ?: "Search failed"
                        )
                    }
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
