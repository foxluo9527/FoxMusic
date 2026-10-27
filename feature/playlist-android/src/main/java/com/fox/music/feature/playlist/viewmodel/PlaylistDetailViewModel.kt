package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.blankj.utilcode.util.TimeUtils
import com.fox.music.core.common.cache.ListCacheKeys
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.constants.AppConstants
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.paging.ArtistMusicPagingSource
import com.fox.music.core.domain.paging.CollectionDetailPagingSource
import com.fox.music.core.domain.paging.FavoriteMusicPagingSource
import com.fox.music.core.domain.paging.PlayHistoryPagingSource
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.domain.repository.PlaylistImportProgress
import com.fox.music.core.domain.repository.PlaylistImportProgressRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.RecommendedMusicCacheManager
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.toplist.TopListMusicStore
import com.fox.music.core.domain.usecase.GetAlbumDetailUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.DeletePlaylistUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.GetRankDetailUseCase
import com.fox.music.core.domain.usecase.RemoveTracksFromPlaylistUseCase
import com.fox.music.core.domain.usecase.ToggleAlbumFavoriteUseCase
import com.fox.music.core.domain.usecase.TogglePlaylistFavoriteUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val getRankDetailUseCase: GetRankDetailUseCase,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val favoriteRepository: FavoriteRepository,
    private val musicRepository: MusicRepository,
    private val toggleAlbumFavoriteUseCase: ToggleAlbumFavoriteUseCase,
    private val togglePlaylistFavoriteUseCase: TogglePlaylistFavoriteUseCase,
    private val getMusicListUseCase: GetMusicListUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase,
    private val artistRepository: ArtistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val recommendedMusicCacheManager: RecommendedMusicCacheManager,
    private val playlistImportProgressRepository: PlaylistImportProgressRepository,
) : MviViewModel<PlaylistDetailState, PlaylistDetailIntent, PlaylistDetailEffect>(PlaylistDetailState()) {

    private val diskOwnerId = CompletableDeferred<Long?>()
    private var latestTracks: List<Music>? = null

    /**
     * 已成功移出的曲目。分页回调在后台线程读它，所以和 UI state 分开用 volatile 快照。
     * 写磁盘快照前再滤一次，避免先发出的详情回包把刚删的歌写回去。
     */
    @Volatile
    private var removedMusicIds: Set<Long> = emptySet()

    private val shouldCacheDetail: Boolean
        get() = detailId > 0L && detailType in CACHED_DETAIL_TYPES

    /** 仅当前用户创建的歌单展示管理菜单 */
    val canManagePlaylist: Boolean
        get() {
            val state = uiState.value
            if (detailType != DetailType.PLAYLIST || detailId <= 0L) return false
            val currentUserId = state.currentUserId ?: return false
            val creatorId = state.headerInfo?.creatorId ?: return false
            return creatorId == currentUserId
        }

    val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L
    private val detailId: Long = playlistId
    val detailType: DetailType = (savedStateHandle.get<String>("type")
        ?.let { runCatching { DetailType.valueOf(it) }.getOrNull() } ?: DetailType.PLAYLIST).also {
        updateState { copy(detailType = it) }
    }

    /** 与路由绑定；推荐歌单在拿到 recommendSeed 后再生成 key，避免不同推荐会话互相覆盖播放列表 */
    private val _playlistKey = MutableStateFlow(initialPlaylistKey())
    val playlistKey: StateFlow<String> = _playlistKey.asStateFlow()

    private fun initialPlaylistKey(): String = when (detailType) {
        DetailType.RECOMMEND -> ""
        DetailType.NEW_MUSIC -> "collection_detail/${Long.MAX_VALUE - 1}"
        DetailType.FAVORITE_MUSIC -> "collection_detail/${DetailType.FAVORITE_MUSIC_ID}"
        DetailType.PLAY_HISTORY -> "collection_detail/${DetailType.PLAY_HISTORY_ID}"
        else -> if (detailId > 0L) "collection_detail/$detailId" else ""
    }

    private fun recommendPlaylistKey(seed: String): String =
        "collection_detail/${Long.MAX_VALUE}/$seed"

    private fun onRecommendSeedLoaded(seed: String) {
        _playlistKey.value = recommendPlaylistKey(seed)
    }

    /** 详情头或首屏响应中已知的歌曲总数；用于尽量一次拉全列表 */
    private val knownTrackCount = AtomicInteger(0)
    private val _knownTrackCountFlow = MutableStateFlow<Int?>(null)

    private fun updateKnownTrackCount(count: Int) {
        if (count > 0) {
            knownTrackCount.set(count)
        }
        _knownTrackCountFlow.value = count.coerceAtLeast(0)
    }

    private fun pagingConfigForKnownTotal(total: Int): PagingConfig {
        val pageSize = total.takeIf { it > 0 } ?: AppConstants.PAGE_SIZE
        return PagingConfig(
            pageSize = pageSize,
            enablePlaceholders = false,
            initialLoadSize = pageSize,
        )
    }

    // 歌曲列表分页数据：能拿到总数时按总数一次加载
    val tracks: Flow<PagingData<Music>> by lazy {
        when (detailType) {
            DetailType.NEW_MUSIC, DetailType.RECOMMEND -> {
                // 固定 100 条：pageSize 与 initialLoadSize 同值（= limit），
                // 进页首屏一次拉满 100，不需要靠滚动再触发下一页
                val topListMaxItems = 100
                getMusicListUseCase.getPagingSource(
                    limit = topListMaxItems,
                    sort = if (detailType == DetailType.NEW_MUSIC) "latest" else "recommend",
                    maxTotalItems = topListMaxItems,
                    onRecommendSeedLoaded = if (detailType == DetailType.RECOMMEND) {
                        ::onRecommendSeedLoaded
                    } else {
                        null
                    },
                    // 整份列表拉取成功后通知外部：回写首页/发现页底层数据并同步正在播放的队列
                    onFirstPageLoaded = { items ->
                        val sort = if (detailType == DetailType.NEW_MUSIC) {
                            TopListMusicStore.SORT_LATEST
                        } else {
                            TopListMusicStore.SORT_RECOMMEND
                        }
                        sendEffect(PlaylistDetailEffect.TopListLoaded(sort, items))
                    },
                ).flow.cachedIn(viewModelScope)
            }

            DetailType.FAVORITE_MUSIC -> {
                _knownTrackCountFlow
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { total ->
                        Pager(
                            config = pagingConfigForKnownTotal(total),
                            pagingSourceFactory = {
                                FavoriteMusicPagingSource(
                                    favoriteRepository = favoriteRepository,
                                    knownTotalProvider = { knownTrackCount.get() },
                                )
                            },
                        ).flow
                    }
                    .cachedIn(viewModelScope)
            }

            DetailType.PLAY_HISTORY -> {
                _knownTrackCountFlow
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { total ->
                        Pager(
                            config = pagingConfigForKnownTotal(total),
                            pagingSourceFactory = {
                                PlayHistoryPagingSource(
                                    musicRepository = musicRepository,
                                    knownTotalProvider = { knownTrackCount.get() },
                                )
                            },
                        ).flow
                    }
                    .cachedIn(viewModelScope)
            }

            DetailType.ARTIST -> {
                _knownTrackCountFlow
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { total ->
                        Pager(
                            config = pagingConfigForKnownTotal(total),
                            pagingSourceFactory = {
                                ArtistMusicPagingSource(
                                    artistRepository = artistRepository,
                                    artistId = detailId,
                                    knownTotalProvider = { knownTrackCount.get() },
                                    onFirstPage = ::onTracksLoaded,
                                )
                            },
                        ).flow
                    }
                    .cachedIn(viewModelScope)
            }

            else -> {
                _knownTrackCountFlow
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { total ->
                        Pager(
                            config = pagingConfigForKnownTotal(total),
                            pagingSourceFactory = {
                                CollectionDetailPagingSource(
                                    playlistRepository = playlistRepository,
                                    albumRepository = albumRepository,
                                    detailId = detailId,
                                    detailType = detailType,
                                    knownTotalProvider = { knownTrackCount.get() },
                                    onFirstPage = if (shouldCacheDetail) ::onTracksLoaded else null,
                                )
                            },
                        ).flow
                    }
                    .cachedIn(viewModelScope)
            }
        }
    }

    // 存储当前加载的歌曲列表用于播放全部功能
    private var currentTrackList: List<Music> = emptyList()

    init {
        viewModelScope.launch {
            val userId = runCatching {
                userPreferencesRepository.userPreferences.first().userId
            }.getOrNull()
            if (!diskOwnerId.isCompleted) diskOwnerId.complete(userId)
            if (userId != null) updateState { copy(currentUserId = userId) }
            if (shouldCacheDetail) restoreDetailCache(userId)
            if (detailId > 0L ||
                detailType == DetailType.FAVORITE_MUSIC ||
                detailType == DetailType.PLAY_HISTORY
            ) {
                sendIntent(PlaylistDetailIntent.LoadHeader)
            }
        }
        if (detailType == DetailType.RECOMMEND) {
            viewModelScope.launch {
                recommendedMusicCacheManager.getCache()?.seed?.takeIf { it.isNotBlank() }?.let {
                    onRecommendSeedLoaded(it)
                }
            }
        }
        if (detailType == DetailType.PLAYLIST && detailId > 0L) {
            viewModelScope.launch {
                var hadActiveImport = false
                var lastDone = 0
                playlistImportProgressRepository.observe(detailId).collect { progress ->
                    if (progress != null) {
                        hadActiveImport = true
                        // 重试批次会把 done 清零，需重置基准
                        if (progress.done < lastDone) {
                            lastDone = progress.done
                        }
                        val doneDelta = progress.done - lastDone
                        lastDone = progress.done
                        updateState {
                            val currentHeader = headerInfo
                            val header = if (doneDelta > 0) {
                                currentHeader?.copy(trackCount = currentHeader.trackCount + doneDelta)
                            } else {
                                currentHeader
                            }
                            copy(importProgress = progress, headerInfo = header)
                        }
                        if (doneDelta > 0) {
                            val total = uiState.value.headerInfo?.trackCount ?: 0
                            if (total > 0) updateKnownTrackCount(total)
                            // 静默刷新列表，不触发下拉刷新动画
                            sendEffect(PlaylistDetailEffect.RefreshTracks)
                        }
                    } else if (hadActiveImport) {
                        hadActiveImport = false
                        lastDone = 0
                        updateState { copy(importProgress = null) }
                        sendEffect(PlaylistDetailEffect.RefreshTracks)
                    } else {
                        updateState { copy(importProgress = null) }
                    }
                }
            }
        }
    }

    override fun handleIntent(intent: PlaylistDetailIntent) {
        when (intent) {
            PlaylistDetailIntent.LoadHeader -> loadHeader()
            PlaylistDetailIntent.Refresh -> loadHeader(isRefresh = true)
            PlaylistDetailIntent.PlayAll -> playAll()
            PlaylistDetailIntent.ToggleFavorite -> toggleFavorite()
            PlaylistDetailIntent.DeletePlaylist -> deletePlaylist()
            is PlaylistDetailIntent.EnterSelectionMode -> enterSelectionMode(intent.musicId)
            is PlaylistDetailIntent.ToggleSelection -> toggleSelection(intent.musicId)
            PlaylistDetailIntent.SelectAll -> selectAll()
            PlaylistDetailIntent.ExitSelectionMode -> exitSelectionMode()
            PlaylistDetailIntent.AddSelectedToQueue -> addSelectedToQueue()
            PlaylistDetailIntent.RemoveSelectedFromPlaylist -> removeSelectedFromPlaylist()
            PlaylistDetailIntent.AddSelectedToPlaylist -> addSelectedToPlaylist()
            PlaylistDetailIntent.DownloadSelected -> downloadSelected()
            PlaylistDetailIntent.PauseImport -> {
                val jobId = uiState.value.importProgress?.jobId ?: return
                viewModelScope.launch { playlistImportProgressRepository.pause(jobId) }
            }
            PlaylistDetailIntent.ResumeImport -> {
                val jobId = uiState.value.importProgress?.jobId ?: return
                viewModelScope.launch { playlistImportProgressRepository.resume(jobId) }
            }
            PlaylistDetailIntent.RetryImport -> {
                val jobId = uiState.value.importProgress?.jobId ?: return
                viewModelScope.launch { playlistImportProgressRepository.retryFailed(jobId) }
            }
            PlaylistDetailIntent.CancelFailedImport -> {
                val jobId = uiState.value.importProgress?.jobId ?: return
                viewModelScope.launch { playlistImportProgressRepository.cancelFailed(jobId) }
            }
        }
    }

    private fun enterSelectionMode(musicId: Long) {
        updateState {
            copy(
                isSelectionMode = true,
                selectedMusicIds = setOf(musicId),
            )
        }
    }

    private fun toggleSelection(musicId: Long) {
        updateState {
            val newSelection = if (musicId in selectedMusicIds) {
                selectedMusicIds - musicId
            } else {
                selectedMusicIds + musicId
            }
            if (newSelection.isEmpty()) {
                copy(isSelectionMode = false, selectedMusicIds = emptySet())
            } else {
                copy(selectedMusicIds = newSelection)
            }
        }
    }

    private fun selectAll() {
        val allIds = currentTrackList.map { it.id }.toSet()
        updateState { copy(selectedMusicIds = allIds) }
    }

    private fun exitSelectionMode() {
        updateState { copy(isSelectionMode = false, selectedMusicIds = emptySet()) }
    }

    private fun getSelectedMusics(): List<Music> {
        val selected = uiState.value.selectedMusicIds
        return currentTrackList.filter { it.id in selected }
    }

    private fun addSelectedToQueue() {
        val musics = getSelectedMusics()
        if (musics.isEmpty()) {
            sendEffect(PlaylistDetailEffect.ShowToast("请先选择歌曲"))
            return
        }
        sendEffect(PlaylistDetailEffect.AddSelectedToQueue(musics))
        exitSelectionMode()
    }

    private fun removeSelectedFromPlaylist() {
        if (!canManagePlaylist) return
        val musicIds = uiState.value.selectedMusicIds.toList()
        if (musicIds.isEmpty()) return
        viewModelScope.launch {
            removeTracksFromPlaylistUseCase(detailId, musicIds)
                .onSuccess {
                    applyLocalTrackRemoval(musicIds.toSet())
                    sendEffect(PlaylistDetailEffect.ShowToast("已从歌单移除"))
                    sendEffect(PlaylistDetailEffect.TracksRemoved(musicIds))
                    sendEffect(PlaylistDetailEffect.RefreshTracks)
                }
                .onError { _, msg ->
                    sendEffect(PlaylistDetailEffect.ShowToast(msg ?: "移除失败"))
                }
        }
    }

    /**
     * 删除接口已经成功：列表、头部数量、内存/磁盘快照马上去掉这些歌。
     * 分页刷新仍会走，但 UI 不再等它回来才消失。
     */
    private fun applyLocalTrackRemoval(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val removedSnapshot = removedMusicIds + ids
        removedMusicIds = removedSnapshot
        val base = currentTrackList.ifEmpty { latestTracks ?: uiState.value.cachedTracks }
        val visible = base.filter { it.id !in removedSnapshot }
        latestTracks = visible
        currentTrackList = visible
        val header = uiState.value.headerInfo
        val removedCount = if (base.isEmpty()) ids.size else base.count { it.id in ids }
        val nextHeader = header?.copy(
            trackCount = (header.trackCount - removedCount).coerceAtLeast(0),
        )
        updateState {
            copy(
                headerInfo = nextHeader ?: headerInfo,
                cachedTracks = if (base.isEmpty()) {
                    cachedTracks.filter { it.id !in removedSnapshot }
                } else {
                    visible
                },
                visibleTracks = if (base.isEmpty()) visibleTracks else visible,
                removedMusicIds = removedSnapshot,
                isSelectionMode = false,
                selectedMusicIds = emptySet(),
            )
        }
        if (nextHeader != null) {
            updateKnownTrackCount(maxOf(nextHeader.trackCount, visible.size))
        }
        val headerToPersist = uiState.value.headerInfo
        if (headerToPersist != null) {
            persistDetailHeader(headerToPersist)
        }
    }

    private fun addSelectedToPlaylist() {
        val musics = getSelectedMusics()
        if (musics.isEmpty()) {
            sendEffect(PlaylistDetailEffect.ShowToast("请先选择歌曲"))
            return
        }
        sendEffect(PlaylistDetailEffect.AddSelectedToPlaylist(musics))
        exitSelectionMode()
    }

    private fun downloadSelected() {
        val musics = getSelectedMusics()
        if (musics.isEmpty()) {
            sendEffect(PlaylistDetailEffect.ShowToast("请先选择歌曲"))
            return
        }
        sendEffect(PlaylistDetailEffect.DownloadSelected(musics))
        exitSelectionMode()
    }

    private fun deletePlaylist() {
        if (!canManagePlaylist) return
        viewModelScope.launch {
            deletePlaylistUseCase(detailId)
                .onSuccess {
                    if (diskOwnerId.isCompleted) {
                        TabPageCache.invalidate(detailCacheKey(diskOwnerId.getCompleted()))
                    }
                    sendEffect(PlaylistDetailEffect.ShowToast("歌单已删除"))
                    sendEffect(PlaylistDetailEffect.NavigateBack)
                }
                .onError { _, msg ->
                    sendEffect(PlaylistDetailEffect.ShowToast(msg ?: "删除失败"))
                }
        }
    }

    fun onMusicClick(music: Music, currentList: List<Music>) {
        val list = currentList.takeIf { it.isNotEmpty() }
            ?: currentTrackList.takeIf { it.isNotEmpty() }
            ?: listOf(music)
        currentTrackList = list
        sendEffect(PlaylistDetailEffect.NavigateToMusic(music, list))
    }

    fun updateCurrentTrackList(list: List<Music>) {
        val visible = list.withoutRemoved()
        // 分页还没回来时会交来空列表，不能把磁盘快照里已经能播的队列清掉。
        if (list.isNotEmpty() || latestTracks == null) {
            currentTrackList = if (removedMusicIds.isEmpty()) list else visible
        }
        if (removedMusicIds.isNotEmpty() && list.isNotEmpty()) {
            latestTracks = visible
            updateState {
                copy(
                    cachedTracks = visible,
                    visibleTracks = visible,
                )
            }
        }
    }

    private fun loadHeader(isRefresh: Boolean = false) {
        viewModelScope.launch {
            updateState {
                copy(
                    isLoading = when {
                        headerInfo != null || cachedTracks.isNotEmpty() -> false
                        isRefresh -> isLoading
                        else -> true
                    },
                    error = null,
                )
            }
            when(detailType) {
                DetailType.ARTIST -> {
                    artistRepository.getArtistDetail(detailId).onSuccess { detail ->
                        val header = HeaderInfo.fromArtist(detail)
                        updateKnownTrackCount(detail.artist.musicCount)
                        updateState {
                            copy(
                                headerInfo = header,
                                isLoading = false,
                            )
                        }
                        persistDetailHeader(header)
                    }.onError { _, msg ->
                        failHeader(msg)
                    }
                }
                DetailType.PLAYLIST -> {
                    getPlaylistDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo =
                            HeaderInfo.fromPlaylist(detail.playlist, DetailType.PLAYLIST)
                        updateKnownTrackCount(
                            maxOf(detail.playlist.trackCount, detail.tracks.total)
                        )
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                        persistDetailHeader(headerInfo)
                    }.onError {_, msg ->
                        failHeader(msg)
                    }
                }

                DetailType.ALBUM -> {
                    getAlbumDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo = HeaderInfo.fromAlbum(detail.album)
                        updateKnownTrackCount(
                            maxOf(detail.album.trackCount, detail.tracks.total)
                        )
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                        persistDetailHeader(headerInfo)
                    }.onError {_, msg ->
                        failHeader(msg)
                    }
                }

                DetailType.RANK -> {
                    getRankDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo = HeaderInfo.fromPlaylist(detail.playlist, DetailType.RANK)
                        updateKnownTrackCount(
                            maxOf(detail.playlist.trackCount, detail.tracks.total)
                        )
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                        persistDetailHeader(headerInfo)
                    }.onError {_, msg ->
                        failHeader(msg)
                    }
                }

                DetailType.RECOMMEND -> {
                    updateState {
                        copy(
                            headerInfo = HeaderInfo(
                                Long.MAX_VALUE,
                                "为您定制推荐歌单",
                                null,
                                null,
                                0,
                                null,
                                null,
                                TimeUtils.millis2String(System.currentTimeMillis(),"yyyy-MM-dd"),
                                false,
                                DetailType.RECOMMEND
                            ), isLoading = false
                        )
                    }
                }

                DetailType.NEW_MUSIC -> {
                    updateState {
                        copy(
                            headerInfo = HeaderInfo(
                                Long.MAX_VALUE - 1,
                                "最新歌曲为您呈现",
                                null,
                                null,
                                0,
                                null,
                                null,
                                TimeUtils.millis2String(System.currentTimeMillis(),"yyyy-MM-dd"),
                                false,
                                DetailType.NEW_MUSIC
                            ), isLoading = false
                        )
                    }
                }

                DetailType.FAVORITE_MUSIC -> {
                    favoriteRepository.getFavoriteMusics(page = 1, limit = 1)
                        .onSuccess { data ->
                            updateKnownTrackCount(data.total)
                            updateState {
                                copy(
                                    headerInfo = HeaderInfo(
                                        id = DetailType.FAVORITE_MUSIC_ID,
                                        title = "我的收藏",
                                        coverImage = null,
                                        description = "收藏的歌曲",
                                        trackCount = data.total,
                                        creatorName = null,
                                        createdAt = null,
                                        isFavorite = false,
                                        detailType = DetailType.FAVORITE_MUSIC,
                                    ),
                                    isLoading = false,
                                )
                            }
                        }
                        .onError { _, msg ->
                            updateKnownTrackCount(0)
                            updateState {
                                copy(
                                    headerInfo = HeaderInfo(
                                        id = DetailType.FAVORITE_MUSIC_ID,
                                        title = "我的收藏",
                                        coverImage = null,
                                        description = "收藏的歌曲",
                                        trackCount = 0,
                                        creatorName = null,
                                        createdAt = null,
                                        isFavorite = false,
                                        detailType = DetailType.FAVORITE_MUSIC,
                                    ),
                                    isLoading = false,
                                    error = msg,
                                )
                            }
                        }
                }

                DetailType.PLAY_HISTORY -> {
                    musicRepository.getPlayHistory(page = 1, limit = 1)
                        .onSuccess { data ->
                            updateKnownTrackCount(data.total)
                            updateState {
                                copy(
                                    headerInfo = HeaderInfo(
                                        id = DetailType.PLAY_HISTORY_ID,
                                        title = "历史播放",
                                        coverImage = null,
                                        description = "最近听过的歌曲",
                                        trackCount = data.total,
                                        creatorName = null,
                                        createdAt = null,
                                        isFavorite = false,
                                        detailType = DetailType.PLAY_HISTORY,
                                    ),
                                    isLoading = false,
                                )
                            }
                        }
                        .onError { _, msg ->
                            updateKnownTrackCount(0)
                            updateState {
                                copy(
                                    headerInfo = HeaderInfo(
                                        id = DetailType.PLAY_HISTORY_ID,
                                        title = "历史播放",
                                        coverImage = null,
                                        description = "最近听过的歌曲",
                                        trackCount = 0,
                                        creatorName = null,
                                        createdAt = null,
                                        isFavorite = false,
                                        detailType = DetailType.PLAY_HISTORY,
                                    ),
                                    isLoading = false,
                                    error = msg,
                                )
                            }
                        }
                }
            }
        }
    }

    private fun playAll() {
        if (currentTrackList.isNotEmpty()) {
            sendEffect(PlaylistDetailEffect.PlayAllTracks(currentTrackList))
        } else {
            sendEffect(PlaylistDetailEffect.ShowToast("暂无歌曲"))
        }
    }

    private fun toggleFavorite() {
        val currentHeader = uiState.value.headerInfo ?: return
        if (uiState.value.isFavoriteLoading) return
        viewModelScope.launch {
            updateState { copy(isFavoriteLoading = true) }
            val result = when (detailType) {
                DetailType.ALBUM -> toggleAlbumFavoriteUseCase(detailId)
                DetailType.PLAYLIST, DetailType.RANK -> togglePlaylistFavoriteUseCase(detailId)
                else -> {
                    updateState { copy(isFavoriteLoading = false) }
                    return@launch
                }
            }
            result.onSuccess {
                val updated = currentHeader.copy(isFavorite = !currentHeader.isFavorite)
                updateState {
                    copy(
                        headerInfo = updated,
                        isFavoriteLoading = false,
                    )
                }
                persistDetailHeader(updated)
                sendEffect(
                    PlaylistDetailEffect.ShowToast(
                        if (!currentHeader.isFavorite) "已收藏" else "已取消收藏"
                    )
                )
            }.onError { _, msg ->
                updateState { copy(isFavoriteLoading = false) }
                sendEffect(PlaylistDetailEffect.ShowToast(msg ?: "操作失败"))
            }
        }
    }

    private suspend fun onTracksLoaded(items: List<Music>) {
        if (!shouldCacheDetail) return
        val visible = items.withoutRemoved()
        latestTracks = visible
        currentTrackList = visible
        val hideRemoved = removedMusicIds.isNotEmpty()
        updateState {
            copy(
                cachedTracks = visible,
                visibleTracks = if (hideRemoved) visible else visibleTracks,
            )
        }
        val header = uiState.value.headerInfo ?: return
        persistSnapshot(header, visible)
    }

    private suspend fun restoreDetailCache(owner: Long?) {
        val snap = withContext(Dispatchers.IO) {
            val raw = TabPageCache.readRaw(detailCacheKey(owner)) ?: return@withContext null
            DetailListSnapshot.decode(raw)
        } ?: return
        if (snap.id != detailId) return
        val header = snap.toHeader()
        latestTracks = snap.tracks
        if (snap.tracks.isNotEmpty()) currentTrackList = snap.tracks
        updateKnownTrackCount(maxOf(header.trackCount, snap.tracks.size))
        updateState {
            copy(
                headerInfo = header,
                cachedTracks = snap.tracks,
                isLoading = false,
                error = null,
            )
        }
    }

    private fun persistDetailHeader(header: HeaderInfo) {
        if (!shouldCacheDetail) return
        val tracks = (latestTracks ?: uiState.value.cachedTracks).withoutRemoved()
        viewModelScope.launch {
            persistSnapshot(header, tracks)
        }
    }

    private suspend fun persistSnapshot(header: HeaderInfo, tracks: List<Music>) {
        val owner = diskOwnerId.await()
        val payload = withContext(Dispatchers.Default) {
            // 编码前再读一次，盖住「回包已经开始写、删除刚刚成功」的窗口。
            DetailListSnapshot.from(header, tracks.withoutRemoved()).encode()
        }
        TabPageCache.putRaw(detailCacheKey(owner), payload)
    }

    private fun List<Music>.withoutRemoved(): List<Music> {
        val removed = removedMusicIds
        if (removed.isEmpty()) return this
        return filter { it.id !in removed }
    }

    private fun failHeader(message: String?) {
        if (knownTrackCount.get() <= 0) {
            updateKnownTrackCount(0)
        }
        updateState {
            copy(
                isLoading = false,
                error = if (headerInfo == null) message ?: "加载失败" else null,
            )
        }
    }

    private fun detailCacheKey(owner: Long?) =
        ListCacheKeys.key(owner, "tracks", detailType.name, detailId.toString())

    private companion object {
        val CACHED_DETAIL_TYPES = setOf(
            DetailType.PLAYLIST,
            DetailType.RANK,
            DetailType.ALBUM,
            DetailType.ARTIST,
        )
    }
}
