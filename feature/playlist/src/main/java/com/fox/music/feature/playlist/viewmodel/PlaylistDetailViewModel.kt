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
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.usecase.GetAlbumDetailUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.GetRankDetailUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val detailType: DetailType = DetailType.PLAYLIST,
) : UiState

sealed interface PlaylistDetailIntent : UiIntent {
    data object LoadHeader: PlaylistDetailIntent
    data object PlayAll: PlaylistDetailIntent
    data object ToggleFavorite: PlaylistDetailIntent
}

sealed interface PlaylistDetailEffect : UiEffect {
    data class NavigateToMusic(val music: Music, val musicList: List<Music>): PlaylistDetailEffect
    data class PlayAllTracks(val musicList: List<Music>): PlaylistDetailEffect
    data class ShowToast(val message: String): PlaylistDetailEffect
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val getRankDetailUseCase: GetRankDetailUseCase,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val getMusicListUseCase: GetMusicListUseCase,
) : MviViewModel<PlaylistDetailState, PlaylistDetailIntent, PlaylistDetailEffect>(PlaylistDetailState()) {

    private val detailId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L
    val detailType: DetailType = (savedStateHandle.get<String>("type")
        ?.let {runCatching {DetailType.valueOf(it)}.getOrNull()} ?: DetailType.PLAYLIST).also {
        updateState {copy(detailType = it)}
    }

    // 歌曲列表分页数据
    val tracks: Flow<PagingData<Music>> by lazy {
        if (detailType == DetailType.NEW_MUSIC || detailType == DetailType.RECOMMEND) {
            getMusicListUseCase.getPagingSource(
                sort = if (detailType == DetailType.NEW_MUSIC) "latest" else "recommend"
            ).flow.cachedIn(viewModelScope)
        } else {
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

    // 存储当前加载的歌曲列表用于播放全部功能
    private var currentTrackList: List<Music> = emptyList()

    init {
        if (detailId > 0L) {
            viewModelScope.launch {sendIntent(PlaylistDetailIntent.LoadHeader)}
        }
    }

    override fun handleIntent(intent: PlaylistDetailIntent) {
        when (intent) {
            PlaylistDetailIntent.LoadHeader -> loadHeader()
            PlaylistDetailIntent.PlayAll -> playAll()
            PlaylistDetailIntent.ToggleFavorite -> toggleFavorite()
        }
    }

    fun onMusicClick(music: Music, currentList: List<Music>) {
        currentTrackList = currentList
        sendEffect(PlaylistDetailEffect.NavigateToMusic(music, currentList))
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
                                TimeUtils.millis2String(System.currentTimeMillis(),"yyyy-MM-dd"),
                                false,
                                DetailType.NEW_MUSIC
                            ), isLoading = false
                        )
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
        viewModelScope.launch {
            val currentHeader = uiState.value.headerInfo ?: return@launch
            when(detailType) {
                DetailType.ALBUM -> {
                    albumRepository.toggleFavorite(detailId).onSuccess {
                        updateState {
                            copy(headerInfo = currentHeader.copy(isFavorite = ! currentHeader.isFavorite))
                        }
                        sendEffect(
                            PlaylistDetailEffect.ShowToast(
                                if (! currentHeader.isFavorite) "已收藏" else "已取消收藏"
                            )
                        )
                    }.onError {_, msg ->
                        sendEffect(PlaylistDetailEffect.ShowToast(msg ?: "操作失败"))
                    }
                }

                else -> {
                    // TODO: 实现歌单/排行榜的收藏功能
                    sendEffect(PlaylistDetailEffect.ShowToast("收藏功能开发中"))
                }
            }
        }
    }
}
