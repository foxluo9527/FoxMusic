package com.fox.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.domain.repository.DownloadRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.AddTrackToPlaylistUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicActionsUiState(
    val actionMusic: Music? = null,
    val showActionSheet: Boolean = false,
    val showPlaylistPicker: Boolean = false,
    val showReportSheet: Boolean = false,
    val pendingMusicIds: List<Long> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val isReportSubmitting: Boolean = false,
)

sealed interface MusicActionsEffect {
    data class ShowToast(val message: String) : MusicActionsEffect
    data class NavigateToArtist(val artistId: Long) : MusicActionsEffect
    data object RequestCreatePlaylist : MusicActionsEffect
}

@HiltViewModel
class MusicActionsViewModel @Inject constructor(
    private val musicController: MusicController,
    private val addTrackToPlaylistUseCase: AddTrackToPlaylistUseCase,
    private val submitReportUseCase: SubmitReportUseCase,
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    private val downloadRepository: DownloadRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicActionsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<MusicActionsEffect>()
    val effect: SharedFlow<MusicActionsEffect> = _effect.asSharedFlow()

    fun showMusicActions(music: Music) {
        _uiState.update {
            it.copy(actionMusic = music, showActionSheet = true)
        }
    }

    fun dismissActionSheet() {
        _uiState.update { it.copy(showActionSheet = false) }
    }

    fun playNext() {
        val music = _uiState.value.actionMusic ?: return
        musicController.addToQueue(music)
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.ShowToast("已添加到下一首播放"))
        }
        dismissActionSheet()
    }

    fun showAddToPlaylist(musicIds: List<Long>) {
        if (musicIds.isEmpty()) return
        _uiState.update {
            it.copy(
                pendingMusicIds = musicIds,
                showPlaylistPicker = true,
                isPlaylistsLoading = true,
            )
        }
        loadPlaylists()
    }

    fun showAddToPlaylistForCurrentMusic() {
        val music = _uiState.value.actionMusic ?: return
        if (music.isThirdParty) {
            viewModelScope.launch {
                _effect.emit(MusicActionsEffect.ShowToast("第三方歌曲暂不支持添加到平台歌单"))
            }
            return
        }
        _uiState.update { it.copy(showActionSheet = false) }
        showAddToPlaylist(listOf(music.id))
    }

    fun dismissPlaylistPicker() {
        _uiState.update {
            it.copy(showPlaylistPicker = false, pendingMusicIds = emptyList())
        }
    }

    fun showReportSheet() {
        _uiState.update {
            it.copy(showActionSheet = false, showReportSheet = true)
        }
    }

    fun dismissReportSheet() {
        _uiState.update { it.copy(showReportSheet = false) }
    }

    fun submitReport(reason: String, description: String?) {
        val music = _uiState.value.actionMusic ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReportSubmitting = true) }
            submitReportUseCase(
                targetType = "music",
                targetId = music.id,
                reason = reason,
                description = description,
            ).onSuccess {
                dismissReportSheet()
                _effect.emit(MusicActionsEffect.ShowToast("举报已提交"))
            }.onError { _, msg ->
                _effect.emit(MusicActionsEffect.ShowToast(msg ?: "举报提交失败"))
            }
            _uiState.update { it.copy(isReportSubmitting = false) }
        }
    }

    fun addTracksToPlaylist(playlistId: Long) {
        val musicIds = _uiState.value.pendingMusicIds
        if (musicIds.isEmpty()) return
        viewModelScope.launch {
            addTrackToPlaylistUseCase(playlistId, musicIds)
                .onSuccess {
                    dismissPlaylistPicker()
                    _effect.emit(MusicActionsEffect.ShowToast("已添加到歌单"))
                }
                .onError { _, msg ->
                    _effect.emit(MusicActionsEffect.ShowToast(msg ?: "添加失败"))
                }
        }
    }

    fun requestCreatePlaylist() {
        dismissPlaylistPicker()
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.RequestCreatePlaylist)
        }
    }

    fun addAllToQueue(musics: List<Music>) {
        if (musics.isEmpty()) return
        musicController.addAllToQueue(musics)
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.ShowToast("已添加 ${musics.size} 首到播放列表"))
        }
    }

    fun downloadMusics(musics: List<Music>) {
        val downloadable = musics.filterNot { MediaUrlResolver.isLocalMedia(it.url) }
        if (downloadable.isEmpty()) return
        viewModelScope.launch {
            downloadRepository.enqueue(downloadable)
            _effect.emit(MusicActionsEffect.ShowToast("已加入下载队列"))
        }
    }

    fun downloadCurrentMusic() {
        val music = _uiState.value.actionMusic ?: return
        downloadMusics(listOf(music))
        dismissActionSheet()
    }

    fun onArtistClick(artistId: Long) {
        val music = _uiState.value.actionMusic
        if (music?.isThirdParty == true) {
            viewModelScope.launch {
                _effect.emit(MusicActionsEffect.ShowToast("第三方歌曲暂不支持跳转艺人详情"))
            }
            return
        }
        dismissActionSheet()
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.NavigateToArtist(artistId))
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            val userId = userPreferencesRepository.userPreferences.first().userId
            getPlaylistListUseCase(userId)
                .onSuccess { playlists ->
                    _uiState.update {
                        it.copy(playlists = playlists, isPlaylistsLoading = false)
                    }
                }
                .onError { _, _ ->
                    _uiState.update {
                        it.copy(playlists = emptyList(), isPlaylistsLoading = false)
                    }
                }
        }
    }
}
