package com.fox.music.feature.home

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.domain.toplist.TopListMusicStore
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlayHistoryUseCase
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val getPlayHistoryUseCase: GetPlayHistoryUseCase,
    private val searchRepository: SearchRepository,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userRepository: UserRepository,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var currentMusicList: List<Music> = emptyList()
    private var currentUserId: Long? = null

    /** 上一次落盘快照的「为你精选」首批 id 序列，用于跳过重复写入 */
    private var lastSnapshotMusicIds: List<Long>? = null

    init {
        // 先注册快照的磁盘编解码器，随后的 read 才能在冷启动时解析上一进程落盘的快照
        registerHomePageCacheCodec()
        viewModelScope.launch {
            sendIntent(HomeIntent.Load)
            userPreferencesRepository.userPreferences.collect { prefs ->
                // 首页头部问候语/头像首字母跟着账号走：换号/退出登录要立刻改口，
                // 但不能每帧都 updateState（collect 会为任意偏好变更重发）。
                if (prefs.username != currentState.userName) {
                    updateState { copy(userName = prefs.username) }
                }
                if (prefs.userId != currentUserId) {
                    currentUserId = prefs.userId
                    sendIntent(HomeIntent.Refresh)
                    // 右上角头像 URL：登录态变化时按 userId 拉一次资料（换号/退出立刻改口）
                    refreshAvatarUrl(prefs.userId)
                }
            }
        }
        // 详情页把整份「为你精选」（最多 100 条）拉回后：首页底层列表用整份数据，
        // 展示仍只取前 5 条，点歌入队的歌单也随之为完整列表
        viewModelScope.launch {
            TopListMusicStore.lists
                .map { it[TopListMusicStore.SORT_RECOMMEND].orEmpty() }
                .distinctUntilChanged()
                .collect { items ->
                    if (items.isEmpty()) return@collect
                    lastSnapshotMusicIds = null
                    updateCurrentMusicList(items)
                    updateState { copy(recommendedMusicCache = items.take(HOME_RECOMMEND_MUSIC_COUNT)) }
                }
        }
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> loadContent(isRefresh = false)
            HomeIntent.Refresh -> loadContent(isRefresh = true)
            is HomeIntent.OnPlaylistClick -> {
                sendEffect(HomeEffect.NavigateToPlaylist(intent.playlist.id))
            }
            is HomeIntent.OnAlbumClick -> {
                sendEffect(HomeEffect.NavigateToAlbum(intent.album.id))
            }
        }
    }

    fun updateCurrentMusicList(list: List<Music>) {
        // 详情页已同步整份列表时，首页分页首屏（仅 5 条）不能把底层列表缩回去
        val synced = TopListMusicStore.snapshot(TopListMusicStore.SORT_RECOMMEND)
        val effective = if (synced.size > list.size) synced else list
        currentMusicList = effective
        // 分页数据到手即回填快照的「为你精选」首批：下次进首页不必等分页首屏请求
        if (effective.isNotEmpty()) {
            // 首批内容没变就跳过（滚动 append 时本方法也会进来），避免反复写盘
            val ids = effective.take(HOME_RECOMMEND_MUSIC_COUNT).map { it.id }
            if (ids == lastSnapshotMusicIds) return
            lastSnapshotMusicIds = ids
            TabPageCache.update(
                key = TabPageKeys.HOME,
                create = {
                    HomePageSnapshot(
                        ownerUserId = currentUserId,
                        hotKeywords = currentState.hotKeywords,
                        recentPlays = currentState.recentPlays,
                        recommendedPlaylists = currentState.recommendedPlaylists,
                        recommendedAlbums = currentState.recommendedAlbums,
                        recommendedMusic = effective.take(HOME_RECOMMEND_MUSIC_COUNT),
                    )
                },
                transform = { snapshot ->
                    if (snapshot.ownerUserId == currentUserId) {
                        snapshot.copy(recommendedMusic = effective.take(HOME_RECOMMEND_MUSIC_COUNT))
                    } else {
                        snapshot
                    }
                },
            )
        }
    }

    /** 首页「为你精选」点歌：队列用推荐列表，来源固定记为「为你精选」。 */
    fun onMusicClick(music: Music) {
        val list = currentMusicList.takeIf { it.isNotEmpty() } ?: listOf(music)
        sendEffect(
            HomeEffect.NavigateToMusic(
                music = music,
                musicList = list,
                source = PlaybackPlaylistSource(
                    kind = PlaybackSourceKind.RECOMMEND,
                    id = DetailType.RECOMMEND_ID,
                    title = "为你精选",
                ),
            )
        )
    }

    /** 首页「最近在听」点歌：队列用最近播放列表，不设置歌单来源。 */
    fun onRecentMusicClick(music: Music) {
        val recent = uiState.value.recentPlays.map { it.music }
        val playList = recent.takeIf { it.isNotEmpty() }
            ?: currentMusicList.takeIf { it.isNotEmpty() }
            ?: listOf(music)
        sendEffect(HomeEffect.NavigateToMusic(music, playList, source = null))
    }

    fun onSearchClick() {
        sendEffect(HomeEffect.NavigateToSearch)
    }

    fun onPlaylistMoreClick() {
        sendEffect(HomeEffect.NavigateToPlaylistCategory)
    }

    fun onAlbumMoreClick() {
        sendEffect(HomeEffect.NavigateToAlbumCategory)
    }

    /** 拉一次当前用户资料拿头像 URL（右上角头像展示用）；失败静默，回落首字母。 */
    private fun refreshAvatarUrl(userId: Long?) {
        viewModelScope.launch {
            val profile = when (val result = userId?.let { userRepository.getUserHomeProfile(it) }) {
                is Result.Success -> result.data
                else -> null
            }
            updateState { copy(avatarUrl = profile?.avatar) }
        }
    }

    private fun loadContent(isRefresh: Boolean = false) {
        viewModelScope.launch {
            // 缓存优先：命中上一次的快照就先铺旧数据（带刷新指示），网络回来后整体替换，
            // 避免切 Tab 回来时整页先空一下。快照带账号维度，换账号不用上一个账号的数据。
            val cached = TabPageCache.readAsync<HomePageSnapshot>(TabPageKeys.HOME)
                ?.takeIf { it.ownerUserId == currentUserId }
            if (cached != null) {
                updateState {
                    copy(
                        hotKeywords = cached.hotKeywords,
                        recentPlays = cached.recentPlays,
                        recommendedPlaylists = cached.recommendedPlaylists,
                        recommendedAlbums = cached.recommendedAlbums,
                        recommendedMusicCache = cached.recommendedMusic,
                        // 已有内容 → 走「后台刷新」语义（顶部转圈），而不是整页 loading 空态
                        isLoading = false,
                        isRefreshing = true,
                        error = null,
                    )
                }
            } else {
                updateState { copy(isLoading = true, error = null) }
            }

            when (val result = searchRepository.getHotKeywords(limit = 10)) {
                is Result.Success -> updateState { copy(hotKeywords = result.data) }
                else -> {}
            }

            when (val result = getPlayHistoryUseCase(page = 1, limit = 12)) {
                is Result.Success -> updateState { copy(recentPlays = result.data.list) }
                else -> {}
            }

            when (val result = playlistRepository.getRecommendedPlaylists(page = 1, limit = 8)) {
                is Result.Success -> updateState { copy(recommendedPlaylists = result.data.list) }
                is Result.Error -> updateState { copy(error = result.message) }
                else -> {}
            }

            updateState {
                copy(
                    recommendedMusic = getMusicListUseCase.getPagingSource(
                        limit = 5,
                        sort = "recommend",
                    ).flow.cachedIn(viewModelScope),
                )
            }

            when (val result = albumRepository.getAlbumList(page = 1, limit = 8, sort = "hot")) {
                is Result.Success -> updateState { copy(recommendedAlbums = result.data.list) }
                else -> {}
            }

            // 落快照：下次进首页先渲染这一份。「为你精选」分页的首屏还没回来时，
            // 沿用快照里已有的那批（分页到手后会再回填一次，见 updateCurrentMusicList）。
            val keptMusic = currentState.recommendedMusicCache.takeIf { it.isNotEmpty() }
                ?: TabPageCache.read<HomePageSnapshot>(TabPageKeys.HOME)?.recommendedMusic.orEmpty()
            TabPageCache.put(
                key = TabPageKeys.HOME,
                value = HomePageSnapshot(
                    ownerUserId = currentUserId,
                    hotKeywords = currentState.hotKeywords,
                    recentPlays = currentState.recentPlays,
                    recommendedPlaylists = currentState.recommendedPlaylists,
                    recommendedAlbums = currentState.recommendedAlbums,
                    recommendedMusic = keptMusic,
                ),
            )

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }

    private companion object {
        /** 「为你精选」在首页展示的条数（与 HomeContent 的截断一致） */
        const val HOME_RECOMMEND_MUSIC_COUNT = 5
    }
}
