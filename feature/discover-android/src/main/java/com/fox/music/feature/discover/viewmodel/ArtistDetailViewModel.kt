package com.fox.music.feature.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.cache.ListCacheKeys
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.GetArtistDetailUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.Music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArtistDetailUseCase: GetArtistDetailUseCase,
    private val artistRepository: ArtistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<ArtistDetailState, ArtistDetailIntent, ArtistDetailEffect>(ArtistDetailState()) {

    private val artistId: Long = savedStateHandle.get<Long>("artistId")
        ?: savedStateHandle.get<String>("artistId")?.toLongOrNull()
        ?: 0L
    private val json = Json { ignoreUnknownKeys = true }
    private var ownerUserId: Long? = null

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
            ownerUserId = runCatching {
                userPreferencesRepository.userPreferences.first().userId
            }.getOrNull()
            val cached = readDetail(ownerUserId)
            if (cached != null) {
                updateState { copy(artistDetail = cached, isLoading = false, error = null) }
            } else {
                updateState { copy(isLoading = true, error = null) }
            }
            when (val result = getArtistDetailUseCase(artistId)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            artistDetail = result.data,
                            isLoading = false,
                            error = null,
                        )
                    }
                    writeDetail(ownerUserId, result.data)
                }
                is Result.Error -> {
                    if (cached == null && currentState.artistDetail == null) {
                        updateState {
                            copy(
                                isLoading = false,
                                error = result.message ?: "加载失败",
                            )
                        }
                    } else {
                        updateState { copy(isLoading = false) }
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun detailKey(owner: Long?) = ListCacheKeys.key(owner, "artist", "detail", artistId.toString())

    private suspend fun readDetail(owner: Long?): ArtistDetail? {
        val raw = TabPageCache.readRawAsync(detailKey(owner)) ?: return null
        return runCatching { json.decodeFromString(ArtistDetail.serializer(), raw) }.getOrNull()
    }

    private fun writeDetail(owner: Long?, detail: ArtistDetail) {
        TabPageCache.putRaw(detailKey(owner), json.encodeToString(ArtistDetail.serializer(), detail))
    }

    private fun toggleFavorite() {
        val artist = currentState.artist ?: return
        viewModelScope.launch {
            updateState { copy(isFavoriteLoading = true) }
            when (val result = artistRepository.toggleFavorite(artist.id)) {
                is Result.Success -> {
                    val updated = currentState.artistDetail?.copy(
                        artist = artist.copy(isFavorite = !artist.isFavorite),
                    )
                    updateState {
                        copy(
                            artistDetail = updated,
                            isFavoriteLoading = false,
                        )
                    }
                    updated?.let { writeDetail(ownerUserId, it) }
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
