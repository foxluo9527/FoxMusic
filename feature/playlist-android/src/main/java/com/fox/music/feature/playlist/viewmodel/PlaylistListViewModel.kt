package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.GetPlaylistCategoriesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistsPagingUseCase
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val getPlaylistCategoriesUseCase: GetPlaylistCategoriesUseCase,
    private val getPlaylistsPagingUseCase: GetPlaylistsPagingUseCase
) : MviViewModel<PlaylistListState, PlaylistListIntent, PlaylistListEffect>(PlaylistListState()) {

    override fun handleIntent(intent: PlaylistListIntent) {
        when (intent) {
            PlaylistListIntent.Load -> loadCategories()
            is PlaylistListIntent.SelectCategory -> selectCategory(intent.index)
        }
    }

    fun onPlaylistClick(playlist: Playlist) {
        sendEffect(PlaylistListEffect.NavigateToPlaylist(playlist))
    }

    fun onBackClick() {
        sendEffect(PlaylistListEffect.NavigateBack)
    }

    private fun loadCategories() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            getPlaylistCategoriesUseCase().onSuccess { categories ->
                updateState { copy(categories = categories, isLoading = false) }
                // 加载推荐歌单（默认第一个 tab）
                loadPlaylists()
            }.onError { _, msg ->
                updateState { copy(isLoading = false, error = msg ?: "Failed to load categories") }
            }
        }
    }

    private fun selectCategory(index: Int) {
        if (index == uiState.value.selectedCategoryIndex) return
        updateState { copy(selectedCategoryIndex = index) }
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val state = uiState.value

        // 第一个 tab（index=0）是推荐歌单，categoryId 为 null
        // 其他 tab 使用对应分类的 ID
        val categoryId = if (state.selectedCategoryIndex == 0) {
            null
        } else {
            state.categories.getOrNull(state.selectedCategoryIndex - 1)?.id
        }

        updateState {
            copy(
                playlists = getPlaylistsPagingUseCase(categoryId).cachedIn(viewModelScope)
            )
        }
    }
}
