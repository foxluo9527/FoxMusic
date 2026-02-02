package com.fox.music.feature.playlist

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPlaylistsUseCase
import com.fox.music.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistListState(
    val myPlaylists: List<Playlist> = emptyList(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface PlaylistListIntent : UiIntent {
    data object Load : PlaylistListIntent
}

sealed interface PlaylistListEffect : UiEffect {
    data class NavigateToPlaylist(val playlist: Playlist) : PlaylistListEffect
}

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    private val getRecommendedPlaylistsUseCase: GetRecommendedPlaylistsUseCase
) : MviViewModel<PlaylistListState, PlaylistListIntent, PlaylistListEffect>(PlaylistListState()) {

    init {
        viewModelScope.launch { sendIntent(PlaylistListIntent.Load) }
    }

    override fun handleIntent(intent: PlaylistListIntent) {
        when (intent) {
            PlaylistListIntent.Load -> load()
        }
    }

    fun onPlaylistClick(playlist: Playlist) {
        sendEffect(PlaylistListEffect.NavigateToPlaylist(playlist))
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            getPlaylistListUseCase().onSuccess { list ->
                updateState { copy(myPlaylists = list, isLoading = false) }
            }.onError { _, msg ->
                updateState { copy(isLoading = false, error = msg ?: "Failed") }
            }
            getRecommendedPlaylistsUseCase(limit = 20).onSuccess { data ->
                updateState { copy(recommendedPlaylists = data.list) }
            }
        }
    }
}
