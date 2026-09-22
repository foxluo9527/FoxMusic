package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.usecase.GetFavoritesUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.feature.social.CreatePostSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectPostMusicSearchState(
    val query: String = "",
    val suggestions: List<SearchSuggest> = emptyList(),
    val items: List<Music> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val isSearchFocused: Boolean = false,
) : UiState {
    val showSuggestions: Boolean
        get() = isSearchFocused && !hasSearched && suggestions.isNotEmpty()
}

sealed interface SelectPostMusicSearchIntent : UiIntent {
    data class QueryChange(val query: String) : SelectPostMusicSearchIntent
    data object Search : SelectPostMusicSearchIntent
    data class SelectSuggest(val suggest: SearchSuggest) : SelectPostMusicSearchIntent
    data class FocusChanged(val focused: Boolean) : SelectPostMusicSearchIntent
    data class Select(val music: Music) : SelectPostMusicSearchIntent
}

sealed interface SelectPostMusicSearchEffect : UiEffect {
    data class MusicSelected(val music: Music) : SelectPostMusicSearchEffect
    data class ShowToast(val message: String) : SelectPostMusicSearchEffect
    data object ClearSearchFocus : SelectPostMusicSearchEffect
}

@HiltViewModel
class SelectPostMusicSearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val getMusicListUseCase: GetMusicListUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val selectionStore: CreatePostSelectionStore,
) : MviViewModel<SelectPostMusicSearchState, SelectPostMusicSearchIntent, SelectPostMusicSearchEffect>(
    SelectPostMusicSearchState(),
) {
    private var suggestJob: Job? = null

    override fun handleIntent(intent: SelectPostMusicSearchIntent) {
        when (intent) {
            is SelectPostMusicSearchIntent.QueryChange -> {
                updateState { copy(query = intent.query, hasSearched = false, items = emptyList()) }
                scheduleSuggest(intent.query)
            }
            SelectPostMusicSearchIntent.Search -> {
                updateState { copy(suggestions = emptyList(), isSearchFocused = false) }
                performSearch()
            }
            is SelectPostMusicSearchIntent.SelectSuggest -> {
                updateState {
                    copy(
                        query = intent.suggest.text,
                        suggestions = emptyList(),
                        isSearchFocused = false,
                    )
                }
                performSearch(keyword = intent.suggest.text)
                sendEffect(SelectPostMusicSearchEffect.ClearSearchFocus)
            }
            is SelectPostMusicSearchIntent.FocusChanged -> {
                updateState { copy(isSearchFocused = intent.focused) }
            }
            is SelectPostMusicSearchIntent.Select -> {
                selectionStore.selectMusic(intent.music)
                sendEffect(SelectPostMusicSearchEffect.MusicSelected(intent.music))
            }
        }
    }

    private fun performSearch(keyword: String? = null) {
        val q = keyword?.trim() ?: currentState.query.trim()
        if (q.isEmpty()) return

        viewModelScope.launch {
            updateState { copy(isLoading = true, hasSearched = true, query = q) }
            when (val result = getMusicListUseCase(1, 50, q)) {
                is Result.Success -> {
                    val favoriteIds = favoriteMusicIds()
                    val items = result.data.list.map { music ->
                        music.copy(isFavorite = music.isFavorite || music.id in favoriteIds)
                    }
                    updateState { copy(items = items, isLoading = false) }
                }
                is Result.Error -> {
                    updateState { copy(isLoading = false) }
                    sendEffect(
                        SelectPostMusicSearchEffect.ShowToast(
                            result.exception.message ?: "搜索失败",
                        ),
                    )
                }
                is Result.Loading -> Unit
            }
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

    private suspend fun favoriteMusicIds(): Set<Long> =
        when (val result = getFavoritesUseCase.getMusics(1, 200)) {
            is Result.Success -> result.data.list.map { it.id }.toSet()
            else -> emptySet()
        }
}
