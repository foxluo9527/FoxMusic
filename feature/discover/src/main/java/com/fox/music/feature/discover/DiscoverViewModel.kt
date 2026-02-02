package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlaylistCategoriesUseCase
import com.fox.music.core.domain.usecase.GetCategoryPlaylistsUseCase
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverState(
    val categories: List<PlaylistCategory> = emptyList(),
    val newMusic: List<Music> = emptyList(),
    val hotMusic: List<Music> = emptyList(),
    val categoryPlaylists: Map<Long, List<Playlist>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface DiscoverIntent : UiIntent {
    data object Load : DiscoverIntent
}

sealed interface DiscoverEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : DiscoverEffect
    data class NavigateToPlaylist(val playlist: Playlist) : DiscoverEffect
    data class NavigateToCategory(val category: PlaylistCategory) : DiscoverEffect
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val getPlaylistCategoriesUseCase: GetPlaylistCategoriesUseCase,
    private val getCategoryPlaylistsUseCase: GetCategoryPlaylistsUseCase
) : MviViewModel<DiscoverState, DiscoverIntent, DiscoverEffect>(DiscoverState()) {

    init {
        viewModelScope.launch { sendIntent(DiscoverIntent.Load) }
    }

    override fun handleIntent(intent: DiscoverIntent) {
        when (intent) {
            DiscoverIntent.Load -> load()
        }
    }

    fun onMusicClick(music: Music) = sendEffect(DiscoverEffect.NavigateToMusic(music))
    fun onPlaylistClick(playlist: Playlist) = sendEffect(DiscoverEffect.NavigateToPlaylist(playlist))

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            getPlaylistCategoriesUseCase().onSuccess { list ->
                updateState { copy(categories = list) }
            }
            getMusicListUseCase(limit = 20, sort = "latest").onSuccess { data ->
                updateState { copy(newMusic = data.list) }
            }
            getMusicListUseCase(limit = 20, sort = "hot").onSuccess { data ->
                updateState { copy(hotMusic = data.list) }
            }
            updateState { copy(isLoading = false) }
        }
    }
}
