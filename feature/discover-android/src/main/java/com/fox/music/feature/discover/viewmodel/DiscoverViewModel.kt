package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private fun loadContent(isRefresh: Boolean) {
        viewModelScope.launch {
            updateState {
                copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null,
                )
            }

            when (val result = searchRepository.getHotKeywords(limit = 10)) {
                is Result.Success -> updateState { copy(hotKeywords = result.data) }
                else -> Unit
            }

            // 榜单共 5 个（飙升/新歌/原创/热歌/欧美热歌），接口按创建时间倒序返回，limit 需覆盖全部
            when (val result = playlistRepository.getRanks(page = 1, limit = 5)) {
                is Result.Success -> {
                    updateState { copy(ranks = result.data) }
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

            when (val result = getMusicListUseCase(page = 1, limit = 25, sort = "latest")) {
                is Result.Success -> {
                    updateState { copy(newMusic = result.data.list) }
                }
                else -> {}
            }

            when (val result = artistRepository.getArtistList(page = 1, limit = 4, sort = "hot")) {
                is Result.Success -> {
                    updateState { copy(hotArtists = result.data.list) }
                }
                else -> {}
            }

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }
}
