package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.toplist.TopListMusicStore
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        // 先注册快照的磁盘编解码器，随后的 read 才能在冷启动时解析上一进程落盘的快照
        registerDiscoverPageCacheCodec()
        viewModelScope.launch {
            sendIntent(DiscoverIntent.Load)
        }
        // 详情页把整份「新歌速递」（最多 100 条）拉回后：探索页底层列表用整份数据，
        // 展示仍只取前 5 条，点歌入队的歌单也随之为完整列表
        viewModelScope.launch {
            TopListMusicStore.lists
                .map { it[TopListMusicStore.SORT_LATEST].orEmpty() }
                .distinctUntilChanged()
                .collect { items ->
                    if (items.isEmpty()) return@collect
                    updateState { copy(newMusic = items) }
                    TabPageCache.update(
                        key = TabPageKeys.DISCOVER,
                        create = {
                            DiscoverPageSnapshot(
                                hotKeywords = currentState.hotKeywords,
                                ranks = currentState.ranks,
                                rankDetails = currentState.rankDetails,
                                newMusic = items,
                                hotArtists = currentState.hotArtists,
                            )
                        },
                        transform = { it.copy(newMusic = items) },
                    )
                }
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
            // 缓存优先：命中上一次的快照就先铺旧数据（带刷新指示），新数据回来后整体替换
            val cached = TabPageCache.readAsync<DiscoverPageSnapshot>(TabPageKeys.DISCOVER)
            if (cached != null) {
                updateState {
                    copy(
                        hotKeywords = cached.hotKeywords,
                        ranks = cached.ranks,
                        rankDetails = cached.rankDetails,
                        newMusic = cached.newMusic,
                        hotArtists = cached.hotArtists,
                        // 已有内容 → 走「后台刷新」语义（顶部转圈），而不是整页 loading 空态
                        isLoading = false,
                        isRefreshing = true,
                        error = null,
                    )
                }
            } else {
                updateState {
                    copy(
                        isLoading = !isRefresh,
                        isRefreshing = isRefresh,
                        error = null,
                    )
                }
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
                    // 详情页已同步整份「新歌速递」（最多 100 条）时，这里的首屏 25 条不能把它缩回去
                    val synced = TopListMusicStore.snapshot(TopListMusicStore.SORT_LATEST)
                    val latest = if (synced.size > result.data.list.size) synced else result.data.list
                    updateState { copy(newMusic = latest) }
                }
                else -> {}
            }

            when (val result = artistRepository.getArtistList(page = 1, limit = 4, sort = "hot")) {
                is Result.Success -> {
                    updateState { copy(hotArtists = result.data.list) }
                }
                else -> {}
            }

            // 落快照：下次进发现页先渲染这一份
            TabPageCache.put(
                key = TabPageKeys.DISCOVER,
                value = DiscoverPageSnapshot(
                    hotKeywords = currentState.hotKeywords,
                    ranks = currentState.ranks,
                    rankDetails = currentState.rankDetails,
                    newMusic = currentState.newMusic,
                    hotArtists = currentState.hotArtists,
                ),
            )

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }
}
