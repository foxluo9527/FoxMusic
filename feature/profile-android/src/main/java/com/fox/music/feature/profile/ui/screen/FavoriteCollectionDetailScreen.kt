package com.fox.music.feature.profile.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.domain.usecase.GetFavoritesUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

const val FAVORITE_DETAIL_TYPE_ARG = "type"

const val FAVORITE_DETAIL_ROUTE = "favorite_detail/{$FAVORITE_DETAIL_TYPE_ARG}"

fun favoriteDetailRoute(type: String): String = "favorite_detail/$type"

const val FAVORITE_DETAIL_TYPE_PLAYLISTS = "playlists"
const val FAVORITE_DETAIL_TYPE_ALBUMS = "albums"
const val FAVORITE_DETAIL_TYPE_ARTISTS = "artists"

@HiltViewModel
class FavoriteCollectionDetailViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
) : ViewModel() {

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loadedType: String? = null

    fun load(type: String) {
        if (loadedType == type) return
        loadedType = type
        viewModelScope.launch {
            isLoading = true
            error = null
            when (type) {
                FAVORITE_DETAIL_TYPE_PLAYLISTS -> getFavoritesUseCase.getPlaylists(limit = 200)
                    .onSuccess { playlists = it.list }
                    .onError { _, msg -> error = msg }

                FAVORITE_DETAIL_TYPE_ALBUMS -> getFavoritesUseCase.getAlbums(limit = 200)
                    .onSuccess { albums = it.list }
                    .onError { _, msg -> error = msg }

                else -> getFavoritesUseCase.getArtists(limit = 200)
                    .onSuccess { artists = it.list }
                    .onError { _, msg -> error = msg }
            }
            isLoading = false
        }
    }
}

@Composable
fun FavoriteCollectionDetailScreen(
    type: String,
    onBack: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    viewModel: FavoriteCollectionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(type) { viewModel.load(type) }

    val collectionType = when (type) {
        FAVORITE_DETAIL_TYPE_PLAYLISTS -> FavoriteCollectionType.PLAYLISTS
        FAVORITE_DETAIL_TYPE_ALBUMS -> FavoriteCollectionType.ALBUMS
        else -> FavoriteCollectionType.ARTISTS
    }
    val title = when (collectionType) {
        FavoriteCollectionType.PLAYLISTS -> "收藏的歌单"
        FavoriteCollectionType.ALBUMS -> "收藏的专辑"
        FavoriteCollectionType.ARTISTS -> "收藏的艺术家"
    }

    FavoriteCollectionDetailContent(
        title = title,
        type = collectionType,
        onBack = onBack,
        playlists = viewModel.playlists,
        albums = viewModel.albums,
        artists = viewModel.artists,
        isLoading = viewModel.isLoading,
        error = viewModel.error,
        onPlaylistClick = onPlaylistClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
    )
}
