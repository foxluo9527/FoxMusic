package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetPostTagsUseCase
import com.fox.music.core.model.music.Tag
import com.fox.music.feature.social.CreatePostSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectPostTagState(
    val query: String = "",
    val matches: List<Tag> = emptyList(),
    val isLoading: Boolean = false,
) : UiState

sealed interface SelectPostTagIntent : UiIntent {
    data class QueryChange(val query: String) : SelectPostTagIntent
    data class Select(val tag: Tag) : SelectPostTagIntent
    data object ConfirmCreate : SelectPostTagIntent
}

sealed interface SelectPostTagEffect : UiEffect {
    data class TagSelected(val tag: Tag) : SelectPostTagEffect
    data object Finished : SelectPostTagEffect
}

@HiltViewModel
class SelectPostTagViewModel @Inject constructor(
    private val getPostTagsUseCase: GetPostTagsUseCase,
    private val selectionStore: CreatePostSelectionStore,
) : MviViewModel<SelectPostTagState, SelectPostTagIntent, SelectPostTagEffect>(
    SelectPostTagState(),
) {
    private var searchJob: Job? = null

    override fun handleIntent(intent: SelectPostTagIntent) {
        when (intent) {
            is SelectPostTagIntent.QueryChange -> {
                updateState { copy(query = intent.query) }
                scheduleSearch(intent.query)
            }
            is SelectPostTagIntent.Select -> {
                selectionStore.selectTag(intent.tag)
                sendEffect(SelectPostTagEffect.TagSelected(intent.tag))
            }
            SelectPostTagIntent.ConfirmCreate -> {
                val name = currentState.query.trim().removePrefix("#")
                if (name.isBlank()) {
                    sendEffect(SelectPostTagEffect.Finished)
                    return
                }
                val existing = currentState.matches.firstOrNull {
                    it.name.equals(name, ignoreCase = true)
                }
                // id=0 means pending create; backend creates/associates on publish.
                val tag = existing ?: Tag(id = 0L, name = name)
                selectionStore.selectTag(tag)
                sendEffect(SelectPostTagEffect.TagSelected(tag))
            }
        }
    }

    private fun scheduleSearch(raw: String) {
        searchJob?.cancel()
        val keyword = raw.trim().removePrefix("#")
        if (keyword.isBlank()) {
            updateState { copy(matches = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            updateState { copy(isLoading = true) }
            when (val result = getPostTagsUseCase(limit = 30, keyword = keyword)) {
                is Result.Success -> updateState {
                    copy(matches = result.data, isLoading = false)
                }
                else -> updateState { copy(matches = emptyList(), isLoading = false) }
            }
        }
    }
}
