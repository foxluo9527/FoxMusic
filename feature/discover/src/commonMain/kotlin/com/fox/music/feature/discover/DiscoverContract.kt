package com.fox.music.feature.discover

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail

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
