package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.blankj.utilcode.util.TimeUtils
import com.fox.music.core.common.constants.AppConstants
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.paging.CollectionDetailPagingSource
import com.fox.music.core.domain.paging.FavoriteMusicPagingSource
import com.fox.music.core.domain.paging.PlayHistoryPagingSource
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.domain.repository.PlaylistImportProgress
import com.fox.music.core.domain.repository.PlaylistImportProgressRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.RecommendedMusicCacheManager
import com.fox.music.core.domain.repository.UserPreferencesRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 头部信息，统一封装歌单/专辑/排行榜的基本信息
 */
data class HeaderInfo(
    val id: Long,
    val title: String,
    val coverImage: String?,
    val description: String?,
    val trackCount: Int,
    val creatorName: String?,
    val creatorId: Long? = null,
    val createdAt: String?,
    val isFavorite: Boolean,
    val detailType: DetailType,
    // 专辑特有
    val artists: String? = null,
    val releaseDate: String? = null,
) {
    companion object {
        fun fromPlaylist(playlist: Playlist, type: DetailType): HeaderInfo {
            return HeaderInfo(
                id = playlist.id,
                title = playlist.title,
                coverImage = playlist.coverImage,
                description = playlist.description,
                trackCount = playlist.trackCount,
                creatorName = playlist.creator?.nickname,
                creatorId = playlist.creatorId ?: playlist.creator?.id,
                createdAt = playlist.createdAt,
                isFavorite = playlist.isFavorite,
                detailType = type
            )
        }

        fun fromAlbum(album: Album): HeaderInfo {
            return HeaderInfo(
                id = album.id,
                title = album.title,
                coverImage = album.coverImage,
                description = album.description,
                trackCount = album.trackCount,
                creatorName = null,
                createdAt = album.createdAt,
                isFavorite = album.isFavorite,
                detailType = DetailType.ALBUM,
                artists = album.artists.joinToString(", ") {it.name},
                releaseDate = album.releaseDate
            )
        }
    }
}

data class PlaylistDetailState(
    val headerInfo: HeaderInfo? = null,
    val currentUserId: Long? = null,
    val isLoading: Boolean = false,
    val isFavoriteLoading: Boolean = false,
    val error: String? = null,
    val detailType: DetailType = DetailType.PLAYLIST,
    val isSelectionMode: Boolean = false,
    val selectedMusicIds: Set<Long> = emptySet(),
    val importProgress: PlaylistImportProgress? = null,
) : UiState

sealed interface PlaylistDetailIntent : UiIntent {
    data object LoadHeader: PlaylistDetailIntent
    data object Refresh : PlaylistDetailIntent
    data object PlayAll: PlaylistDetailIntent
    data object ToggleFavorite: PlaylistDetailIntent
    data object DeletePlaylist : PlaylistDetailIntent
    data class EnterSelectionMode(val musicId: Long) : PlaylistDetailIntent
    data class ToggleSelection(val musicId: Long) : PlaylistDetailIntent
    data object SelectAll : PlaylistDetailIntent
    data object ExitSelectionMode : PlaylistDetailIntent
    data object AddSelectedToQueue : PlaylistDetailIntent
    data object RemoveSelectedFromPlaylist : PlaylistDetailIntent
    data object AddSelectedToPlaylist : PlaylistDetailIntent
    data object DownloadSelected : PlaylistDetailIntent
    data object PauseImport : PlaylistDetailIntent
    data object ResumeImport : PlaylistDetailIntent
    data object RetryImport : PlaylistDetailIntent
    data object CancelFailedImport : PlaylistDetailIntent
}

sealed interface PlaylistDetailEffect : UiEffect {
    data class NavigateToMusic(val music: Music, val musicList: List<Music>): PlaylistDetailEffect
    data class PlayAllTracks(val musicList: List<Music>): PlaylistDetailEffect
    data class ShowToast(val message: String): PlaylistDetailEffect
    data object NavigateBack : PlaylistDetailEffect
    data class AddSelectedToQueue(val musics: List<Music>) : PlaylistDetailEffect
    data class AddSelectedToPlaylist(val musicIds: List<Long>) : PlaylistDetailEffect
    data class DownloadSelected(val musics: List<Music>) : PlaylistDetailEffect
    data object RefreshTracks : PlaylistDetailEffect
}

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
    private val userPreferencesRepository: UserPreferencesRepository,
    private val recommendedMusicCacheManager: RecommendedMusicCacheManager,
    private val playlistImportProgressRepository: PlaylistImportProgressRepository,
) : MviViewModel<PlaylistDetailState, PlaylistDetailIntent, PlaylistDetailEffect>(PlaylistDetailState()) {

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
                getMusicListUseCase.getPagingSource(
                    sort = if (detailType == DetailType.NEW_MUSIC) "latest" else "recommend",
                    maxTotalItems = 100,
                    onRecommendSeedLoaded = if (detailType == DetailType.RECOMMEND) {
                        ::onRecommendSeedLoaded
                    } else {
                        null
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
            userPreferencesRepository.userPreferences.first().userId?.let { userId ->
                updateState { copy(currentUserId = userId) }
            }
        }
        if (detailId > 0L ||
            detailType == DetailType.FAVORITE_MUSIC ||
            detailType == DetailType.PLAY_HISTORY
        ) {
            viewModelScope.launch { sendIntent(PlaylistDetailIntent.LoadHeader) }
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
                            val header = if (doneDelta > 0) {
                                headerInfo?.copy(trackCount = headerInfo.trackCount + doneDelta)
                            } else {
                                headerInfo
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
                    sendEffect(PlaylistDetailEffect.ShowToast("已从歌单移除"))
                    sendEffect(PlaylistDetailEffect.RefreshTracks)
                    exitSelectionMode()
                }
                .onError { _, msg ->
                    sendEffect(PlaylistDetailEffect.ShowToast(msg ?: "移除失败"))
                }
        }
    }

    private fun addSelectedToPlaylist() {
        val musicIds = uiState.value.selectedMusicIds.toList()
        if (musicIds.isEmpty()) {
            sendEffect(PlaylistDetailEffect.ShowToast("请先选择歌曲"))
            return
        }
        sendEffect(PlaylistDetailEffect.AddSelectedToPlaylist(musicIds))
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
        currentTrackList = list
    }

    private fun loadHeader(isRefresh: Boolean = false) {
        viewModelScope.launch {
            updateState {
                copy(
                    isLoading = if (isRefresh && headerInfo != null) isLoading else true,
                    error = null,
                )
            }
            when(detailType) {
                DetailType.PLAYLIST -> {
                    getPlaylistDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo =
                            HeaderInfo.fromPlaylist(detail.playlist, DetailType.PLAYLIST)
                        updateKnownTrackCount(
                            maxOf(detail.playlist.trackCount, detail.tracks.total)
                        )
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                    }.onError {_, msg ->
                        updateKnownTrackCount(0)
                        updateState {copy(isLoading = false, error = msg ?: "加载失败")}
                    }
                }

                DetailType.ALBUM -> {
                    getAlbumDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo = HeaderInfo.fromAlbum(detail.album)
                        updateKnownTrackCount(
                            maxOf(detail.album.trackCount, detail.tracks.total)
                        )
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                    }.onError {_, msg ->
                        updateKnownTrackCount(0)
                        updateState {copy(isLoading = false, error = msg ?: "加载失败")}
                    }
                }

                DetailType.RANK -> {
                    getRankDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo = HeaderInfo.fromPlaylist(detail.playlist, DetailType.RANK)
                        updateKnownTrackCount(
                            maxOf(detail.playlist.trackCount, detail.tracks.total)
                        )
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                    }.onError {_, msg ->
                        updateKnownTrackCount(0)
                        updateState {copy(isLoading = false, error = msg ?: "加载失败")}
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
                updateState {
                    copy(
                        headerInfo = currentHeader.copy(isFavorite = !currentHeader.isFavorite),
                        isFavoriteLoading = false,
                    )
                }
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
}
