package com.fox.music.feature.playlist.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.blankj.utilcode.util.TimeUtils
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.paging.CollectionDetailPagingSource
import com.fox.music.core.domain.paging.FavoriteMusicPagingSource
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.domain.repository.PlaylistRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) : UiState

sealed interface PlaylistDetailIntent : UiIntent {
    data object LoadHeader: PlaylistDetailIntent
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
    private val toggleAlbumFavoriteUseCase: ToggleAlbumFavoriteUseCase,
    private val togglePlaylistFavoriteUseCase: TogglePlaylistFavoriteUseCase,
    private val getMusicListUseCase: GetMusicListUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
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

    /** 与路由绑定，不依赖 header 接口返回，避免 playlistKey 为空导致点播无响应 */
    val playlistKey: String
        get() = when (detailType) {
            DetailType.RECOMMEND -> "collection_detail/${Long.MAX_VALUE}"
            DetailType.NEW_MUSIC -> "collection_detail/${Long.MAX_VALUE - 1}"
            DetailType.FAVORITE_MUSIC -> "collection_detail/${DetailType.FAVORITE_MUSIC_ID}"
            else -> if (detailId > 0L) "collection_detail/$detailId" else ""
        }

    // 歌曲列表分页数据
    val tracks: Flow<PagingData<Music>> by lazy {
        when (detailType) {
            DetailType.NEW_MUSIC, DetailType.RECOMMEND -> {
                getMusicListUseCase.getPagingSource(
                    sort = if (detailType == DetailType.NEW_MUSIC) "latest" else "recommend",
                    maxTotalItems = 100,
                ).flow.cachedIn(viewModelScope)
            }

            DetailType.FAVORITE_MUSIC -> {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        initialLoadSize = 20,
                    ),
                    pagingSourceFactory = {
                        FavoriteMusicPagingSource(
                            favoriteRepository = favoriteRepository,
                        )
                    },
                ).flow.cachedIn(viewModelScope)
            }

            else -> {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        initialLoadSize = 20
                    ),
                    pagingSourceFactory = {
                        CollectionDetailPagingSource(
                            playlistRepository = playlistRepository,
                            albumRepository = albumRepository,
                            detailId = detailId,
                            detailType = detailType
                        )
                    }
                ).flow.cachedIn(viewModelScope)
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
        if (detailId > 0L || detailType == DetailType.FAVORITE_MUSIC) {
            viewModelScope.launch { sendIntent(PlaylistDetailIntent.LoadHeader) }
        }
    }

    override fun handleIntent(intent: PlaylistDetailIntent) {
        when (intent) {
            PlaylistDetailIntent.LoadHeader -> loadHeader()
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

    private fun loadHeader() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            when(detailType) {
                DetailType.PLAYLIST -> {
                    getPlaylistDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo =
                            HeaderInfo.fromPlaylist(detail.playlist, DetailType.PLAYLIST)
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                    }.onError {_, msg ->
                        updateState {copy(isLoading = false, error = msg ?: "加载失败")}
                    }
                }

                DetailType.ALBUM -> {
                    getAlbumDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo = HeaderInfo.fromAlbum(detail.album)
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                    }.onError {_, msg ->
                        updateState {copy(isLoading = false, error = msg ?: "加载失败")}
                    }
                }

                DetailType.RANK -> {
                    getRankDetailUseCase(detailId, 1, 1).onSuccess {detail ->
                        val headerInfo = HeaderInfo.fromPlaylist(detail.playlist, DetailType.RANK)
                        updateState {copy(headerInfo = headerInfo, isLoading = false)}
                    }.onError {_, msg ->
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
