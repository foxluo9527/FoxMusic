package com.fox.music.feature.playlist.viewmodel

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Album
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class AlbumListState(
    val albums: Flow<PagingData<Album>> = flowOf(PagingData.empty()),
    /** 推荐专辑第一页的磁盘快照。分页还在加载或失败时先用它把格子铺上。 */
    val cachedAlbums: List<Album> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface AlbumListIntent : UiIntent {
    data object Load : AlbumListIntent
}

sealed interface AlbumListEffect : UiEffect {
    data class NavigateToAlbum(val album: Album) : AlbumListEffect
    data object NavigateBack : AlbumListEffect
}
