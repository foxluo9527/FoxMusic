package com.fox.music.feature.home

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPlaylistsUseCase
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val recommendedMusic: List<Music> = emptyList(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Refresh : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : HomeEffect
    data class NavigateToPlaylist(val playlist: Playlist) : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val getRecommendedPlaylistsUseCase: GetRecommendedPlaylistsUseCase
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

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

    fun onPlaylistClick(playlist: Playlist) {
        sendEffect(HomeEffect.NavigateToPlaylist(playlist))
    }

    fun clearError() {
        updateState { copy(error = null) }
    }

    private fun loadContent() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            getMusicListUseCase(limit = 20, sort = "recommend")
                .onSuccess { data ->
                    updateState {
                        copy(
                            recommendedMusic = data.list,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onError { _, message ->
                    updateState {
                        copy(
                            isLoading = false,
                            error = message ?: "Failed to load music"
                        )
                    }
                }
            getRecommendedPlaylistsUseCase(limit = 10)
                .onSuccess { data ->
                    updateState {
                        copy(recommendedPlaylists = data.list)
                    }
                }
                .onError { _, _ -> }
        }
    }
}
