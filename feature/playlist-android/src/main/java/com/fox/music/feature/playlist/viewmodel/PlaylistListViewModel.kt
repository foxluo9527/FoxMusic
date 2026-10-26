package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.ListScrollStateHolder
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.GetPlaylistCategoriesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistsPagingUseCase
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val getPlaylistCategoriesUseCase: GetPlaylistCategoriesUseCase,
    private val getPlaylistsPagingUseCase: GetPlaylistsPagingUseCase
) : MviViewModel<PlaylistListState, PlaylistListIntent, PlaylistListEffect>(PlaylistListState()) {

    /** 详情页返回时 Composable 会重新组合，但 ViewModel 不会重建 —— 滚动位置存在这里才保得住 */
    val scrollState = ListScrollStateHolder()

    /**
     * 每个 tab 的 PagingData 只建一次：`cachedIn` 的缓存绑定在 Flow 实例上，
     * 每次 Load 都 new 一个新 Pager 会让旧缓存作废 → 列表重载 + 回到顶部。
     */
    private val pagingFlowCache = mutableMapOf<Long?, Flow<PagingData<Playlist>>>()

    init {
        loadCategories()
    }

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
        // 分类是静态的，拉一次就够 —— 返回时再拉会白白多一次请求并重置 tab 状态
        if (uiState.value.categories.isNotEmpty()) {
            loadPlaylists()
            return
        }

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

        val flow = pagingFlowCache.getOrPut(categoryId) {
            getPlaylistsPagingUseCase(categoryId).cachedIn(viewModelScope)
        }
        if (state.playlists !== flow) {
            updateState { copy(playlists = flow) }
        }
    }
}
