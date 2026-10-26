package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.ListScrollStateHolder
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.GetAlbumListUseCase
import com.fox.music.core.model.music.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val getAlbumListUseCase: GetAlbumListUseCase,
) : MviViewModel<AlbumListState, AlbumListIntent, AlbumListEffect>(AlbumListState()) {

    /** 详情页返回时 Composable 会重新组合，但 ViewModel 不会重建 —— 滚动位置存在这里才保得住 */
    val scrollState = ListScrollStateHolder()

    /**
     * PagingData 只建一次：`cachedIn` 的缓存绑定在 Flow 实例上，每次 Load 都 new 一个新 Pager
     * 会让旧缓存整体作废，列表就会重载并滚回顶部。
     */
    private val albumsFlow: Flow<PagingData<Album>> by lazy {
        // 推荐专辑页：接后端 sort=recommend 综合分（真实播放量+收藏+曲目数+运营精选）。
        // 此前传 null 走默认 latest，页面实际是「最新上传」而不是「推荐」。
        getAlbumListUseCase.getPagingSource(sort = "recommend").flow.cachedIn(viewModelScope)
    }

    init {
        loadAlbums()
    }

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
        if (uiState.value.albums !== albumsFlow) {
            updateState {
                copy(
                    albums = albumsFlow,
                    isLoading = false,
                    error = null,
                )
            }
        }
    }
}
