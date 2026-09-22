package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.GetAlbumListUseCase
import com.fox.music.core.model.music.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val getAlbumListUseCase: GetAlbumListUseCase,
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
