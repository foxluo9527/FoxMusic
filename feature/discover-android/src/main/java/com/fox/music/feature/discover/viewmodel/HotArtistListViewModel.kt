package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.ListScrollStateHolder
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.GetHotArtistPagingUseCase
import com.fox.music.core.model.music.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class HotArtistListViewModel @Inject constructor(
    private val getHotArtistPagingUseCase: GetHotArtistPagingUseCase,
) : MviViewModel<HotArtistListState, HotArtistListIntent, HotArtistListEffect>(HotArtistListState()) {

    /** 详情页返回时 Composable 会重新组合，但 ViewModel 不会重建 —— 滚动位置存在这里才保得住 */
    val scrollState = ListScrollStateHolder()

    /**
     * PagingData 必须只建一次：`cachedIn` 的缓存绑定在 Flow 实例上，每次 Load 都 new 一个
     * 新 Pager 会让旧缓存整体作废，collectAsLazyPagingItems 收到新 Flow 就从第 1 页重新请求，
     * 表现就是「从详情页返回 → 列表重载 + 回到顶部」。
     */
    private val artistsFlow: Flow<PagingData<Artist>> by lazy {
        getHotArtistPagingUseCase().cachedIn(viewModelScope)
    }

    init {
        loadArtists()
    }

    override fun handleIntent(intent: HotArtistListIntent) {
        when (intent) {
            HotArtistListIntent.Load -> loadArtists()
        }
    }

    fun onArtistClick(artistId: Long) {
        sendEffect(HotArtistListEffect.NavigateToArtist(artistId))
    }

    fun onAllArtistsClick() {
        sendEffect(HotArtistListEffect.NavigateToAllArtists)
    }

    fun onBackClick() {
        sendEffect(HotArtistListEffect.NavigateBack)
    }

    private fun loadArtists() {
        if (uiState.value.artists !== artistsFlow) {
            updateState { copy(artists = artistsFlow) }
        }
    }
}
