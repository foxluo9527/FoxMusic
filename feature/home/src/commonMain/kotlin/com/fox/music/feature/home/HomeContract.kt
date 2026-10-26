package com.fox.music.feature.home

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class HomeState(
    /** 当前登录用户名（首页问候语 + 右上角头像首字母），未登录为 null */
    val userName: String? = null,
    /** 当前登录用户头像 URL：右上角头像优先展示图片，无 URL 时回落首字母/人形图标 */
    val avatarUrl: String? = null,
    val hotKeywords: List<HotKeyword> = emptyList(),
    val recentPlays: List<PlayHistory> = emptyList(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val recommendedMusic: Flow<PagingData<Music>> = flowOf(),
    /**
     * 「为你精选」的上一批缓存：分页 Flow 重建后要等首屏请求回来才有内容，
     * 期间用它顶上（新数据到达后自动让位，见 HomeContent）。
     */
    val recommendedMusicCache: List<Music> = emptyList(),
    val recommendedAlbums: List<Album> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Refresh : HomeIntent
    data class OnPlaylistClick(val playlist: Playlist) : HomeIntent
    data class OnAlbumClick(val album: Album) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToMusic(
        val music: Music,
        val musicList: List<Music>,
        val source: PlaybackPlaylistSource? = null,
    ) : HomeEffect
    data class NavigateToPlaylist(val playlistId: Long) : HomeEffect
    data class NavigateToAlbum(val albumId: Long) : HomeEffect
    data object NavigateToSearch : HomeEffect
    data object NavigateToPlaylistCategory : HomeEffect
    data object NavigateToAlbumCategory : HomeEffect
}
