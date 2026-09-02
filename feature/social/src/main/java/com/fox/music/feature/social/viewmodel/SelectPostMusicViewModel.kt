package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetFavoritesUseCase
import com.fox.music.core.domain.usecase.GetPlayHistoryUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.feature.social.CreatePostSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SelectPostMusicTab {
    FAVORITE,
    RECENT,
}

data class SelectPostMusicState(
    val tab: SelectPostMusicTab = SelectPostMusicTab.FAVORITE,
    val items: List<Music> = emptyList(),
    val isLoading: Boolean = false,
) : UiState

sealed interface SelectPostMusicIntent : UiIntent {
    data object Load : SelectPostMusicIntent
    data class SwitchTab(val tab: SelectPostMusicTab) : SelectPostMusicIntent
    data class Select(val music: Music) : SelectPostMusicIntent
}

sealed interface SelectPostMusicEffect : UiEffect {
    data class MusicSelected(val music: Music) : SelectPostMusicEffect
    data class ShowToast(val message: String) : SelectPostMusicEffect
}

@HiltViewModel
class SelectPostMusicViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getPlayHistoryUseCase: GetPlayHistoryUseCase,
    private val selectionStore: CreatePostSelectionStore,
) : MviViewModel<SelectPostMusicState, SelectPostMusicIntent, SelectPostMusicEffect>(
    SelectPostMusicState(),
) {
    init {
        sendIntent(SelectPostMusicIntent.Load)
    }

    override fun handleIntent(intent: SelectPostMusicIntent) {
        when (intent) {
            SelectPostMusicIntent.Load -> loadCurrent()
            is SelectPostMusicIntent.SwitchTab -> {
                updateState { copy(tab = intent.tab) }
                loadCurrent()
            }
            is SelectPostMusicIntent.Select -> {
                selectionStore.selectMusic(intent.music)
                sendEffect(SelectPostMusicEffect.MusicSelected(intent.music))
            }
        }
    }

    private fun loadCurrent() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            val result: Result<List<Music>> = when (currentState.tab) {
                SelectPostMusicTab.FAVORITE -> when (val r = getFavoritesUseCase.getMusics(1, 50)) {
                    is Result.Success -> Result.Success(r.data.list.map { it.copy(isFavorite = true) })
                    is Result.Error -> Result.Error(r.exception)
                    is Result.Loading -> Result.Loading
                }
                SelectPostMusicTab.RECENT -> when (val r = getPlayHistoryUseCase(1, 50)) {
                    is Result.Success -> {
                        val favoriteIds = favoriteMusicIds()
                        Result.Success(
                            r.data.list.map { item ->
                                val music = item.music
                                music.copy(isFavorite = music.isFavorite || music.id in favoriteIds)
                            },
                        )
                    }
                    is Result.Error -> Result.Error(r.exception)
                    is Result.Loading -> Result.Loading
                }
            }
            when (result) {
                is Result.Success -> updateState { copy(items = result.data, isLoading = false) }
                is Result.Error -> {
                    updateState { copy(isLoading = false) }
                    sendEffect(SelectPostMusicEffect.ShowToast(result.exception.message ?: "加载失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun favoriteMusicIds(): Set<Long> =
        when (val result = getFavoritesUseCase.getMusics(1, 200)) {
            is Result.Success -> result.data.list.map { it.id }.toSet()
            else -> emptySet()
        }
}
