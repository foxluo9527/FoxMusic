package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.cache.ListCacheKeys
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.mvi.ListScrollStateHolder
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.GetHotArtistPagingUseCase
import com.fox.music.core.model.music.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class HotArtistListViewModel @Inject constructor(
    private val getHotArtistPagingUseCase: GetHotArtistPagingUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<HotArtistListState, HotArtistListIntent, HotArtistListEffect>(HotArtistListState()) {

    /** 详情页返回时 Composable 会重新组合，但 ViewModel 不会重建 —— 滚动位置存在这里才保得住 */
    val scrollState = ListScrollStateHolder()

    private val ownerId = CompletableDeferred<Long?>()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * PagingData 必须只建一次：`cachedIn` 的缓存绑定在 Flow 实例上，每次 Load 都 new 一个
     * 新 Pager 会让旧缓存整体作废，collectAsLazyPagingItems 收到新 Flow 就从第 1 页重新请求，
     * 表现就是「从详情页返回 → 列表重载 + 回到顶部」。
     */
    private val artistsFlow: Flow<PagingData<Artist>> by lazy {
        getHotArtistPagingUseCase(
            onFirstPage = { artists ->
                val owner = ownerId.await()
                TabPageCache.putRaw(
                    artistKey(owner),
                    json.encodeToString(ListSerializer(Artist.serializer()), artists),
                )
            },
        ).cachedIn(viewModelScope)
    }

    init {
        viewModelScope.launch {
            val id = runCatching { userPreferencesRepository.userPreferences.first().userId }.getOrNull()
            if (!ownerId.isCompleted) ownerId.complete(id)
            val cached = readArtists(id)
            if (!cached.isNullOrEmpty()) {
                updateState { copy(cachedArtists = cached) }
            }
        }
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

    private fun artistKey(owner: Long?) = ListCacheKeys.key(owner, "artists", "hot", "p1")

    private suspend fun readArtists(owner: Long?): List<Artist>? {
        val raw = TabPageCache.readRawAsync(artistKey(owner)) ?: return null
        return runCatching {
            json.decodeFromString(ListSerializer(Artist.serializer()), raw)
        }.getOrNull()
    }
}
