package com.fox.music.feature.discover

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.Music

data class ArtistDetailState(
    val artistDetail: ArtistDetail? = null,
    val isLoading: Boolean = false,
    val isFavoriteLoading: Boolean = false,
    val error: String? = null,
) : UiState {
    val artist: Artist? get() = artistDetail?.artist
    val hotMusics: List<Music> get() = artistDetail?.hotMusics.orEmpty()
    val albums: List<Album> get() = artistDetail?.albums.orEmpty()
}

sealed interface ArtistDetailIntent : UiIntent {
    data object Load : ArtistDetailIntent
    data object ToggleFavorite : ArtistDetailIntent
}

sealed interface ArtistDetailEffect : UiEffect {
    data class NavigateToMusic(val music: Music, val musicList: List<Music>) : ArtistDetailEffect
    data class NavigateToAlbum(val album: Album) : ArtistDetailEffect
    data class ShowToast(val message: String) : ArtistDetailEffect
}
