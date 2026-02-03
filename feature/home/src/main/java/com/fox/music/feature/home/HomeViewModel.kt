package com.fox.music.feature.home

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.model.Music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val recommendedMusic: Flow<PagingData<Music>>,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Refresh : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
): MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState(flowOf())) {

    init {
        viewModelScope.launch {
            sendIntent(HomeIntent.Load)
        }
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load, HomeIntent.Refresh -> loadContent()
        }
    }

    fun onMusicClick(music: Music) {
        sendEffect(HomeEffect.NavigateToMusic(music))
    }

    private fun loadContent() {
        viewModelScope.launch {
            updateState {
                copy(
                    recommendedMusic = getMusicListUseCase.getPagingSource(
                        limit = 20,
                        sort = "recommend"
                    ).flow.cachedIn(viewModelScope)
                )
            }
        }
    }
}
