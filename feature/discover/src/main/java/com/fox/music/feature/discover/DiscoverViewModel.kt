package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.model.Artist
import com.fox.music.core.model.HotKeyword
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverState(
    val hotKeywords: List<HotKeyword> = emptyList(),
    val ranks: List<Playlist> = emptyList(),
    val rankDetails: Map<Long, PlaylistDetail> = emptyMap(),
    val newMusic: List<Music> = emptyList(),
    val hotArtists: List<Artist> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface DiscoverIntent : UiIntent {
    data object Load : DiscoverIntent
    data object Refresh : DiscoverIntent
    data class OnRankClick(val rank: Playlist) : DiscoverIntent
    data class OnMusicClick(val music: Music) : DiscoverIntent
    data class OnArtistClick(val artist: Artist) : DiscoverIntent
}

sealed interface DiscoverEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : DiscoverEffect
    data class NavigateToRank(val rankId: Long) : DiscoverEffect
    data class NavigateToArtist(val artistId: Long) : DiscoverEffect
    data object NavigateToSearch : DiscoverEffect
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: ArtistRepository,
    private val searchRepository: SearchRepository,
) : MviViewModel<DiscoverState, DiscoverIntent, DiscoverEffect>(DiscoverState()) {

    init {
        viewModelScope.launch {
            sendIntent(DiscoverIntent.Load)
        }
    }

    override fun handleIntent(intent: DiscoverIntent) {
        when (intent) {
            is DiscoverIntent.Load -> loadContent(isRefresh = false)
            is DiscoverIntent.Refresh -> loadContent(isRefresh = true)
            is DiscoverIntent.OnRankClick -> {
                sendEffect(DiscoverEffect.NavigateToRank(intent.rank.id))
            }
            is DiscoverIntent.OnMusicClick -> {
                sendEffect(DiscoverEffect.NavigateToMusic(intent.music))
            }
            is DiscoverIntent.OnArtistClick -> {
                sendEffect(DiscoverEffect.NavigateToArtist(intent.artist.id))
            }
        }
    }

    fun onSearchClick() {
        sendEffect(DiscoverEffect.NavigateToSearch)
    }

    private fun loadContent(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                updateState { copy(isRefreshing = true, error = null) }
            } else {
                updateState { copy(isLoading = true, error = null) }
            }

            // 加载热词
            when (val result = searchRepository.getHotKeywords(limit = 10)) {
                is Result.Success -> {
                    updateState { copy(hotKeywords = result.data) }
                }
                is Result.Error -> {
                    // 忽略热词加载失败
                }
                else -> {}
            }

            // 加载榜单列表（请求3个）
            when (val result = playlistRepository.getRanks(page = 1, limit = 4)) {
                is Result.Success -> {
                    updateState { copy(ranks = result.data) }

                    // 并行加载每个榜单的详情（前4首歌）
                    val rankDetails = result.data.map { rank ->
                        async {
                            when (val detailResult = playlistRepository.getRankDetail(rank.id, page = 1, limit = 4)) {
                                is Result.Success -> rank.id to detailResult.data
                                else -> null
                            }
                        }
                    }.awaitAll().filterNotNull().toMap()

                    updateState { copy(rankDetails = rankDetails) }
                }
                is Result.Error -> {
                    updateState { copy(error = result.message) }
                }
                else -> {}
            }

            // 加载新歌速递
            when (val result = getMusicListUseCase(page = 1, limit = 5, sort = "latest")) {
                is Result.Success -> {
                    updateState { copy(newMusic = result.data.list) }
                }
                is Result.Error -> {
                    // 忽略新歌加载失败
                }
                else -> {}
            }

            // 加载热门歌手（只请求4个）
            when (val result = artistRepository.getArtistList(page = 1, limit = 4, sort = "hot")) {
                is Result.Success -> {
                    updateState { copy(hotArtists = result.data.list) }
                }
                is Result.Error -> {
                    // 忽略歌手加载失败
                }
                else -> {}
            }

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }
}
