package com.fox.music.feature.playlist.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.PlaylistImportProgress
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.Playlist

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
    data object LoadHeader : PlaylistDetailIntent
    data object Refresh : PlaylistDetailIntent
    data object PlayAll : PlaylistDetailIntent
    data object ToggleFavorite : PlaylistDetailIntent
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
    data class NavigateToMusic(val music: Music, val musicList: List<Music>) : PlaylistDetailEffect
    data class PlayAllTracks(val musicList: List<Music>) : PlaylistDetailEffect
    data class ShowToast(val message: String) : PlaylistDetailEffect
    data object NavigateBack : PlaylistDetailEffect
    data class AddSelectedToQueue(val musics: List<Music>) : PlaylistDetailEffect
    data class AddSelectedToPlaylist(val musics: List<Music>) : PlaylistDetailEffect
    data class DownloadSelected(val musics: List<Music>) : PlaylistDetailEffect
    data object RefreshTracks : PlaylistDetailEffect
    /**
     * 为你精选/新歌速递详情页整份列表加载成功。
     * 用于回写首页/发现页的底层列表（展示条数不变）并同步正在播放的队列。
     */
    data class TopListLoaded(val sort: String, val list: List<Music>) : PlaylistDetailEffect
}

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
    val publisher: String? = null,
    val language: String? = null,
    // 歌单/榜单标签（介绍详情页展示）
    val tags: List<String> = emptyList(),
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
                detailType = type,
                tags = playlist.tags.map { it.name },
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
                artists = album.artists.joinToString(", ") { it.name },
                releaseDate = album.releaseDate,
                publisher = album.publisher,
                language = album.language,
            )
        }

        fun fromArtist(detail: ArtistDetail): HeaderInfo {
            val artist = detail.artist
            return HeaderInfo(
                id = artist.id,
                title = artist.name,
                coverImage = artist.coverImage ?: artist.avatar,
                description = artist.description,
                trackCount = artist.musicCount,
                creatorName = null,
                creatorId = null,
                createdAt = artist.createdAt,
                isFavorite = artist.isFavorite,
                detailType = DetailType.ARTIST,
            )
        }
    }
}

fun HeaderInfo.toPlaybackSource(): PlaybackPlaylistSource =
    PlaybackPlaylistSource.fromDetailType(
        type = detailType,
        id = id,
        title = title,
        coverUrl = coverImage,
    )
