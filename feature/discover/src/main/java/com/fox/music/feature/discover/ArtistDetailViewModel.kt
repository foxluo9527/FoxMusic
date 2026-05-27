package com.fox.music.feature.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.usecase.GetArtistDetailUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.Music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArtistDetailUseCase: GetArtistDetailUseCase,
    private val artistRepository: ArtistRepository,
) : MviViewModel<ArtistDetailState, ArtistDetailIntent, ArtistDetailEffect>(ArtistDetailState()) {

    private val artistId: Long = savedStateHandle.get<Long>("artistId")
        ?: savedStateHandle.get<String>("artistId")?.toLongOrNull()
        ?: 0L

    init {
        sendIntent(ArtistDetailIntent.Load)
    }

    override fun handleIntent(intent: ArtistDetailIntent) {
        when (intent) {
            ArtistDetailIntent.Load -> loadDetail()
            ArtistDetailIntent.ToggleFavorite -> toggleFavorite()
        }
    }

    fun onMusicClick(music: Music) {
        sendEffect(ArtistDetailEffect.NavigateToMusic(music, currentState.hotMusics))
    }

    fun onAlbumClick(album: Album) {
        sendEffect(ArtistDetailEffect.NavigateToAlbum(album))
    }

    private fun loadDetail() {
        if (artistId <= 0L) {
            updateState { copy(error = "无效的歌手 ID", isLoading = false) }
            return
        }
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            when (val result = getArtistDetailUseCase(artistId)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            artistDetail = result.data,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                is Result.Error -> {
                    updateState {
                        copy(
                            isLoading = false,
                            error = result.message ?: "加载失败",
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun toggleFavorite() {
        val artist = currentState.artist ?: return
        viewModelScope.launch {
            updateState { copy(isFavoriteLoading = true) }
            when (val result = artistRepository.toggleFavorite(artist.id)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            artistDetail = artistDetail?.copy(
                                artist = artist.copy(isFavorite = !artist.isFavorite),
                            ),
                            isFavoriteLoading = false,
                        )
                    }
                    sendEffect(
                        ArtistDetailEffect.ShowToast(
                            if (!artist.isFavorite) "收藏成功" else "已取消收藏",
                        ),
                    )
                }
                is Result.Error -> {
                    updateState { copy(isFavoriteLoading = false) }
                    sendEffect(
                        ArtistDetailEffect.ShowToast(result.message ?: "操作失败"),
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }
}
