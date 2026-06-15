package com.fox.music.feature.social.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.usecase.CreatePostUseCase
import com.fox.music.core.domain.usecase.GetPostDetailUseCase
import com.fox.music.core.domain.usecase.UpdatePostUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreatePostState(
    val editingPostId: Long? = null,
    val content: String = "",
    val localImageUris: List<Uri> = emptyList(),
    val remoteImageUrls: List<String> = emptyList(),
    val isPublishing: Boolean = false,
    val isLoadingDraft: Boolean = true,
    val publishSucceeded: Boolean = false,
) : UiState {
    val isEditMode: Boolean get() = editingPostId != null
}

sealed interface CreatePostIntent : UiIntent {
    data object LoadDraft : CreatePostIntent
    data class UpdateContent(val content: String) : CreatePostIntent
    data class AddImage(val uri: Uri) : CreatePostIntent
    data class RemoveImage(val uri: Uri) : CreatePostIntent
    data class RemoveRemoteImage(val url: String) : CreatePostIntent
    data object Publish : CreatePostIntent
    data object SaveDraftNow : CreatePostIntent
}

sealed interface CreatePostEffect : UiEffect {
    data class ShowToast(val message: String) : CreatePostEffect
    data object Published : CreatePostEffect
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val postDraftRepository: PostDraftRepository,
    private val createPostUseCase: CreatePostUseCase,
    private val updatePostUseCase: UpdatePostUseCase,
    private val getPostDetailUseCase: GetPostDetailUseCase,
    private val uploadRepository: UploadRepository,
) : MviViewModel<CreatePostState, CreatePostIntent, CreatePostEffect>(CreatePostState()) {

    private val editPostId: Long? = savedStateHandle.get<Long>("postId")?.takeIf { it > 0 }
    private var saveDraftJob: Job? = null

    init {
        sendIntent(CreatePostIntent.LoadDraft)
    }

    override fun handleIntent(intent: CreatePostIntent) {
        when (intent) {
            CreatePostIntent.LoadDraft -> loadInitial()
            is CreatePostIntent.UpdateContent -> {
                updateState { copy(content = intent.content) }
                if (!currentState.isEditMode) scheduleSaveDraft()
            }
            is CreatePostIntent.AddImage -> persistAndAddImage(intent.uri)
            is CreatePostIntent.RemoveImage -> removeImage(intent.uri)
            is CreatePostIntent.RemoveRemoteImage -> removeRemoteImage(intent.url)
            CreatePostIntent.Publish -> publish()
            CreatePostIntent.SaveDraftNow -> saveDraftNow()
        }
    }

    private fun loadInitial() {
        val postId = editPostId
        if (postId != null) {
            loadPostForEdit(postId)
        } else {
            loadDraft()
        }
    }

    private fun loadPostForEdit(postId: Long) {
        viewModelScope.launch {
            when (val result = getPostDetailUseCase(postId)) {
                is Result.Success -> {
                    val post = result.data
                    updateState {
                        copy(
                            editingPostId = postId,
                            content = post.content,
                            remoteImageUrls = post.images,
                            isLoadingDraft = false,
                        )
                    }
                }
                is Result.Error -> {
                    updateState { copy(isLoadingDraft = false) }
                    sendEffect(CreatePostEffect.ShowToast(result.exception.message ?: "加载失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = postDraftRepository.loadDraft()
            updateState {
                copy(
                    content = draft?.content.orEmpty(),
                    localImageUris = draft?.localImagePaths.orEmpty().map(Uri::parse),
                    isLoadingDraft = false,
                )
            }
        }
    }

    private fun persistAndAddImage(uri: Uri) {
        val totalImages = currentState.localImageUris.size + currentState.remoteImageUrls.size
        if (totalImages >= 9) {
            sendEffect(CreatePostEffect.ShowToast("最多选择 9 张图片"))
            return
        }
        viewModelScope.launch {
            runCatching {
                postDraftRepository.persistImage(uri)
            }.onSuccess { persistedUri ->
                updateState {
                    copy(localImageUris = localImageUris + persistedUri)
                }
                if (!currentState.isEditMode) scheduleSaveDraft()
            }.onFailure {
                sendEffect(CreatePostEffect.ShowToast("图片保存失败"))
            }
        }
    }

    private fun removeImage(uri: Uri) {
        updateState {
            copy(localImageUris = localImageUris.filter { it != uri })
        }
        if (!currentState.isEditMode) scheduleSaveDraft()
    }

    private fun removeRemoteImage(url: String) {
        updateState {
            copy(remoteImageUrls = remoteImageUrls.filter { it != url })
        }
    }

    private fun scheduleSaveDraft() {
        saveDraftJob?.cancel()
        saveDraftJob = viewModelScope.launch {
            delay(500)
            saveDraftNow()
        }
    }

    private fun saveDraftNow() {
        if (currentState.isEditMode || currentState.publishSucceeded) return
        viewModelScope.launch {
            val state = currentState
            if (state.content.isBlank() && state.localImageUris.isEmpty()) {
                postDraftRepository.clearDraft()
                return@launch
            }
            postDraftRepository.saveDraft(
                content = state.content,
                localImagePaths = state.localImageUris.map { it.toString() },
            )
        }
    }

    private fun publish() {
        val content = currentState.content.trim()
        if (content.isBlank()) {
            sendEffect(CreatePostEffect.ShowToast("请输入正文内容"))
            return
        }
        viewModelScope.launch {
            updateState { copy(isPublishing = true) }
            val result = if (currentState.isEditMode) {
                updateExistingPost(content)
            } else {
                createPostUseCase(
                    content = content,
                    localImageUris = currentState.localImageUris,
                )
            }
            when (result) {
                is Result.Success -> {
                    if (!currentState.isEditMode) {
                        postDraftRepository.clearDraft()
                    }
                    updateState {
                        copy(
                            isPublishing = false,
                            publishSucceeded = !isEditMode,
                            content = if (isEditMode) content else "",
                            localImageUris = if (isEditMode) localImageUris else emptyList(),
                            remoteImageUrls = if (isEditMode) remoteImageUrls else emptyList(),
                        )
                    }
                    sendEffect(CreatePostEffect.Published)
                }
                is Result.Error -> {
                    updateState { copy(isPublishing = false) }
                    sendEffect(
                        CreatePostEffect.ShowToast(
                            result.exception.message ?: if (currentState.isEditMode) "更新失败" else "发布失败",
                        ),
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun updateExistingPost(content: String): Result<com.fox.music.core.model.social.Post> {
        val postId = currentState.editingPostId ?: return Result.Error(IllegalStateException("无效帖子"))
        val imageUrls = currentState.remoteImageUrls.toMutableList()
        for (uri in currentState.localImageUris) {
            when (val uploadResult = uploadRepository.uploadImage(uri, compress = true)) {
                is Result.Success -> imageUrls.add(uploadResult.data)
                is Result.Error -> return uploadResult
                is Result.Loading -> Unit
            }
        }
        return updatePostUseCase(
            id = postId,
            content = content,
            images = imageUrls,
            status = "published",
        )
    }
}
