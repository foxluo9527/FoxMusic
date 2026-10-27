package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.cache.ListCacheKeys
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.mvi.ListScrollStateHolder
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.GetPlaylistCategoriesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistsPagingUseCase
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val getPlaylistCategoriesUseCase: GetPlaylistCategoriesUseCase,
    private val getPlaylistsPagingUseCase: GetPlaylistsPagingUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<PlaylistListState, PlaylistListIntent, PlaylistListEffect>(PlaylistListState()) {

    /** 详情页返回时 Composable 会重新组合，但 ViewModel 不会重建 —— 滚动位置存在这里才保得住 */
    val scrollState = ListScrollStateHolder()

    /**
     * 每个 tab 的 PagingData 只建一次：`cachedIn` 的缓存绑定在 Flow 实例上，
     * 每次 Load 都 new 一个新 Pager 会让旧缓存作废 → 列表重载 + 回到顶部。
     */
    private val pagingFlowCache = mutableMapOf<Long?, Flow<PagingData<Playlist>>>()
    private val ownerId = CompletableDeferred<Long?>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val id = runCatching { userPreferencesRepository.userPreferences.first().userId }.getOrNull()
            if (!ownerId.isCompleted) ownerId.complete(id)
        }
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
            val owner = ownerId.await()
            val cached = readCategories(owner)
            if (!cached.isNullOrEmpty()) {
                updateState { copy(categories = cached, isLoading = false, error = null) }
                loadPlaylists()
            } else {
                updateState { copy(isLoading = true, error = null) }
            }

            getPlaylistCategoriesUseCase().onSuccess { categories ->
                updateState { copy(categories = categories, isLoading = false) }
                writeCategories(owner, categories)
                loadPlaylists()
            }.onError { _, msg ->
                if (uiState.value.categories.isEmpty()) {
                    updateState { copy(isLoading = false, error = msg ?: "Failed to load categories") }
                } else {
                    updateState { copy(isLoading = false) }
                }
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
        val selectedIndex = state.selectedCategoryIndex

        // 第一个 tab（index=0）是推荐歌单，categoryId 为 null
        // 其他 tab 使用对应分类的 ID
        val categoryId = if (selectedIndex == 0) {
            null
        } else {
            state.categories.getOrNull(selectedIndex - 1)?.id
        }

        val flow = pagingFlowCache.getOrPut(categoryId) {
            getPlaylistsPagingUseCase(
                categoryId = categoryId,
                onFirstPage = { playlists ->
                    val owner = ownerId.await()
                    writePlaylists(owner, categoryId, playlists)
                },
            ).cachedIn(viewModelScope)
        }
        viewModelScope.launch {
            val owner = ownerId.await()
            if (uiState.value.selectedCategoryIndex != selectedIndex) return@launch
            val cached = readPlaylists(owner, categoryId).orEmpty()
            updateState {
                copy(
                    playlists = flow,
                    cachedPlaylists = cached,
                )
            }
        }
    }

    private fun playlistKey(owner: Long?, categoryId: Long?) =
        ListCacheKeys.key(owner, "playlists", categoryId?.toString() ?: "recommend", "p1")

    private suspend fun readCategories(owner: Long?): List<PlaylistCategory>? {
        val raw = TabPageCache.readRawAsync(ListCacheKeys.key(owner, "playlist", "categories")) ?: return null
        return runCatching {
            json.decodeFromString(ListSerializer(PlaylistCategory.serializer()), raw)
        }.getOrNull()
    }

    private fun writeCategories(owner: Long?, categories: List<PlaylistCategory>) {
        TabPageCache.putRaw(
            ListCacheKeys.key(owner, "playlist", "categories"),
            json.encodeToString(ListSerializer(PlaylistCategory.serializer()), categories),
        )
    }

    private suspend fun readPlaylists(owner: Long?, categoryId: Long?): List<Playlist>? {
        val raw = TabPageCache.readRawAsync(playlistKey(owner, categoryId)) ?: return null
        return runCatching {
            json.decodeFromString(ListSerializer(Playlist.serializer()), raw)
        }.getOrNull()
    }

    private fun writePlaylists(owner: Long?, categoryId: Long?, playlists: List<Playlist>) {
        TabPageCache.putRaw(
            playlistKey(owner, categoryId),
            json.encodeToString(ListSerializer(Playlist.serializer()), playlists),
        )
    }
}
