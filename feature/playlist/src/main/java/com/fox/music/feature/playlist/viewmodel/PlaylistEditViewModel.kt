package com.fox.music.feature.playlist.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.UpdatePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistEditState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingCover: Boolean = false,
    val title: String = "",
    val description: String = "",
    /** 服务端封面地址，保存时提交 */
    val coverImage: String? = null,
    /** 裁剪后的本地封面 Uri，用于上传 */
    val previewCoverUri: Uri? = null,
    /** 裁剪后的本地文件，用于即时预览（优先于网络地址） */
    val previewCoverFile: java.io.File? = null,
    val isPublic: Boolean = true,
    val error: String? = null,
) : UiState

sealed interface PlaylistEditIntent : UiIntent {
    data object Load : PlaylistEditIntent
    data class UpdateTitle(val title: String) : PlaylistEditIntent
    data class UpdateDescription(val description: String) : PlaylistEditIntent
    data class UpdateIsPublic(val isPublic: Boolean) : PlaylistEditIntent
    data class SetCoverPreview(val uri: Uri, val file: java.io.File) : PlaylistEditIntent
    data class UploadCover(val uri: Uri) : PlaylistEditIntent
    data object Save : PlaylistEditIntent
}

sealed interface PlaylistEditEffect : UiEffect {
    data class ShowToast(val message: String) : PlaylistEditEffect
    data object Saved : PlaylistEditEffect
}

@HiltViewModel
class PlaylistEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val updatePlaylistUseCase: UpdatePlaylistUseCase,
    private val uploadRepository: UploadRepository,
) : MviViewModel<PlaylistEditState, PlaylistEditIntent, PlaylistEditEffect>(PlaylistEditState()) {

    private val playlistId: Long =
        savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L

    init {
        if (playlistId > 0L) {
            sendIntent(PlaylistEditIntent.Load)
        }
    }

    override fun handleIntent(intent: PlaylistEditIntent) {
        when (intent) {
            PlaylistEditIntent.Load -> load()
            is PlaylistEditIntent.UpdateTitle -> updateState { copy(title = intent.title) }
            is PlaylistEditIntent.UpdateDescription ->
                updateState { copy(description = intent.description) }
            is PlaylistEditIntent.UpdateIsPublic ->
                updateState { copy(isPublic = intent.isPublic) }
            is PlaylistEditIntent.SetCoverPreview ->
                updateState {
                    copy(previewCoverUri = intent.uri, previewCoverFile = intent.file)
                }
            is PlaylistEditIntent.UploadCover -> uploadCover(intent.uri)
            PlaylistEditIntent.Save -> save()
        }
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            getPlaylistDetailUseCase(playlistId, page = 1, limit = 1)
                .onSuccess { detail ->
                    val playlist = detail.playlist
                    updateState {
                        copy(
                            isLoading = false,
                            title = playlist.title,
                            description = playlist.description.orEmpty(),
                            coverImage = playlist.coverImage,
                            isPublic = playlist.isPublic,
                        )
                    }
                }
                .onError { _, msg ->
                    updateState {
                        copy(isLoading = false, error = msg ?: "加载失败")
                    }
                }
        }
    }

    private fun uploadCover(uri: Uri) {
        viewModelScope.launch {
            updateState { copy(isUploadingCover = true) }
            uploadRepository.uploadImage(uri)
                .onSuccess { url ->
                    updateState {
                        copy(
                            coverImage = url,
                            isUploadingCover = false,
                        )
                    }
                    sendEffect(PlaylistEditEffect.ShowToast("封面上传成功"))
                }
                .onError { _, msg ->
                    updateState { copy(isUploadingCover = false) }
                    sendEffect(PlaylistEditEffect.ShowToast(msg ?: "封面上传失败"))
                }
        }
    }

    private fun save() {
        val title = uiState.value.title.trim()
        if (title.isBlank()) {
            sendEffect(PlaylistEditEffect.ShowToast("请输入歌单名称"))
            return
        }
        viewModelScope.launch {
            updateState { copy(isSaving = true) }
            updatePlaylistUseCase(
                id = playlistId,
                title = title,
                description = uiState.value.description.trim().ifBlank { null },
                coverImage = uiState.value.coverImage,
                isPublic = uiState.value.isPublic,
            ).onSuccess {
                updateState { copy(isSaving = false) }
                sendEffect(PlaylistEditEffect.ShowToast("保存成功"))
                sendEffect(PlaylistEditEffect.Saved)
            }.onError { _, msg ->
                updateState { copy(isSaving = false) }
                sendEffect(PlaylistEditEffect.ShowToast(msg ?: "保存失败"))
            }
        }
    }
}
