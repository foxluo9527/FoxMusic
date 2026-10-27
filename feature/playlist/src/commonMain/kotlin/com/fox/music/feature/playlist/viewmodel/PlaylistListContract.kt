package com.fox.music.feature.playlist.viewmodel

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class PlaylistListState(
    val categories: List<PlaylistCategory> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val playlists: Flow<PagingData<Playlist>> = flowOf(PagingData.empty()),
    /** 当前分类第一页的磁盘快照。分页还在加载或失败时先用它把格子铺上。 */
    val cachedPlaylists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface PlaylistListIntent : UiIntent {
    data object Load : PlaylistListIntent
    data class SelectCategory(val index: Int) : PlaylistListIntent
}

sealed interface PlaylistListEffect : UiEffect {
    data class NavigateToPlaylist(val playlist: Playlist) : PlaylistListEffect
    data object NavigateBack : PlaylistListEffect
}
