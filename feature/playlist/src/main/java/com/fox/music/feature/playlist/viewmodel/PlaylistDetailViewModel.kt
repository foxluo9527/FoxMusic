package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.model.Music
import com.fox.music.core.model.PlaylistDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailState(
    val detail: PlaylistDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface PlaylistDetailIntent : UiIntent {
    data object Load : PlaylistDetailIntent
}

sealed interface PlaylistDetailEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : PlaylistDetailEffect
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase
) : MviViewModel<PlaylistDetailState, PlaylistDetailIntent, PlaylistDetailEffect>(PlaylistDetailState()) {

    private val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L

    init {
        viewModelScope.launch { sendIntent(PlaylistDetailIntent.Load) }
    }

    override fun handleIntent(intent: PlaylistDetailIntent) {
        when (intent) {
            PlaylistDetailIntent.Load -> if (playlistId > 0L) load() else Unit
        }
    }

    fun onMusicClick(music: Music) {
        sendEffect(PlaylistDetailEffect.NavigateToMusic(music))
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            getPlaylistDetailUseCase(playlistId).onSuccess { detail ->
                updateState { copy(detail = detail, isLoading = false) }
            }.onError { _, msg ->
                updateState { copy(isLoading = false, error = msg ?: "Failed") }
            }
        }
    }
}
