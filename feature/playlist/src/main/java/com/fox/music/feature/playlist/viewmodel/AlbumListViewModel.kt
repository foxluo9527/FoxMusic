package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetAlbumListUseCase
import com.fox.music.core.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

data class AlbumListState(
    val albums: Flow<PagingData<Album>> = flowOf(PagingData.empty()),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface AlbumListIntent : UiIntent {
    data object Load : AlbumListIntent
}

sealed interface AlbumListEffect : UiEffect {
    data class NavigateToAlbum(val album: Album) : AlbumListEffect
    data object NavigateBack : AlbumListEffect
}

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val getAlbumListUseCase: GetAlbumListUseCase
) : MviViewModel<AlbumListState, AlbumListIntent, AlbumListEffect>(AlbumListState()) {

    override fun handleIntent(intent: AlbumListIntent) {
        when (intent) {
            AlbumListIntent.Load -> loadAlbums()
        }
    }

    fun onAlbumClick(album: Album) {
        sendEffect(AlbumListEffect.NavigateToAlbum(album))
    }

    fun onBackClick() {
        sendEffect(AlbumListEffect.NavigateBack)
    }

    private fun loadAlbums() {
        updateState {
            copy(
                albums = getAlbumListUseCase.getPagingSource().flow.cachedIn(viewModelScope),
                isLoading = false,
                error = null
            )
        }
    }
}
