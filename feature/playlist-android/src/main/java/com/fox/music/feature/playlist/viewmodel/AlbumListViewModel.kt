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
                // 推荐专辑页：接后端 sort=recommend 综合分（真实播放量+收藏+曲目数+运营精选）。
                // 此前传 null 走默认 latest，页面实际是「最新上传」而不是「推荐」。
                albums = getAlbumListUseCase.getPagingSource(sort = "recommend").flow.cachedIn(viewModelScope),
                isLoading = false,
                error = null
            )
        }
    }
}
