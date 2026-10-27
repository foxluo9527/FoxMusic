package com.fox.music.feature.discover

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Artist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class HotArtistListState(
    val artists: Flow<PagingData<Artist>> = flowOf(PagingData.empty()),
    /** 热门歌手第一页的磁盘快照。分页还在加载或失败时先用它把格子铺上。 */
    val cachedArtists: List<Artist> = emptyList(),
) : UiState

sealed interface HotArtistListIntent : UiIntent {
    data object Load : HotArtistListIntent
}

sealed interface HotArtistListEffect : UiEffect {
    data class NavigateToArtist(val artistId: Long) : HotArtistListEffect
    data object NavigateToAllArtists : HotArtistListEffect
    data object NavigateBack : HotArtistListEffect
}
