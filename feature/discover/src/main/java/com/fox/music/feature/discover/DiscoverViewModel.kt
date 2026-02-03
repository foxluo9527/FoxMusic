package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.LoadState
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetCategoryPlaylistsUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlaylistCategoriesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverStateList(
    val newMusic: List<Music> = emptyList(),
    val hotMusic: List<Music> = emptyList(),
    val categoryPages: MutableMap<PlaylistCategory, LoadState<List<Playlist>>> = mutableMapOf(),
    val minePlayList: List<Playlist>? = null,
    var loadNewPage: Int = 1,
    var loadHotPage: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoadMore: Boolean = false,
) : UiState


sealed interface DiscoverIntent : UiIntent {
    data object Load: DiscoverIntent

    data class LoadNew(
        val page: Int,
        val limit: Int = 20,
    ): DiscoverIntent

    data class LoadHot(
        val page: Int,
        val limit: Int = 20,
    ): DiscoverIntent

    data class LoadPlayListByCategory(
        val category: PlaylistCategory,
        val page: Int,
        val limit: Int = 20,
    ): DiscoverIntent
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
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    private val getCategoryPlaylistsUseCase: GetCategoryPlaylistsUseCase,
): MviViewModel<DiscoverStateList, DiscoverIntent, DiscoverEffect>(DiscoverStateList()) {
    init {
        viewModelScope.launch { sendIntent(DiscoverIntent.Load) }
    }

    override fun handleIntent(intent: DiscoverIntent) {
        viewModelScope.launch {
            when(intent) {
                is DiscoverIntent.Load -> load()
                is DiscoverIntent.LoadNew -> {
                    getMusicListUseCase(
                        page = intent.page,
                        limit = intent.limit,
                        sort = "latest"
                    ).onSuccess {data ->
                        updateState {copy(newMusic = data.list)}
                    }
                }

                is DiscoverIntent.LoadHot -> {
                    getMusicListUseCase(
                        page = intent.page,
                        limit = intent.limit,
                        sort = "hot"
                    ).onSuccess {data ->
                        updateState {copy(hotMusic = data.list)}
                    }
                }

                is DiscoverIntent.LoadPlayListByCategory -> {
                    getCategoryPlaylistsUseCase(
                        intent.category.id,
                        intent.page,
                        intent.limit
                    ).onSuccess {data ->
                        updateState {
                            copy(
                                categoryPages = categoryPages.apply {
                                    this[intent.category]?.let {
                                        this[intent.category] = it.success(data = data.list)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun onMusicClick(music: Music) = sendEffect(DiscoverEffect.NavigateToMusic(music))

    fun onPlaylistClick(playlist: Playlist) = sendEffect(DiscoverEffect.NavigateToPlaylist(playlist))

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            getPlaylistCategoriesUseCase().onSuccess { list ->
                updateState {
                    copy(categoryPages = list.associateWith {
                        LoadState<List<Playlist>>().onLoading()
                    }.toMutableMap())
                }
            }
            getPlaylistListUseCase().onSuccess {data ->
                updateState {copy(minePlayList = data)}
            }
            updateState { copy(isLoading = false) }
        }
    }
}
