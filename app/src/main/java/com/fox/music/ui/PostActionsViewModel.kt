package com.fox.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.DeletePostUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.model.social.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostActionsUiState(
    val actionPost: Post? = null,
    val currentUserId: Long? = null,
    val showSelectFriend: Boolean = false,
    val showReportSheet: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val isReportSubmitting: Boolean = false,
    val isDeleting: Boolean = false,
)

sealed interface PostActionsEffect {
    data class ShowToast(val message: String) : PostActionsEffect
    data object NavigateToSelectFriend : PostActionsEffect
    data class NavigateToEditPost(val postId: Long) : PostActionsEffect
    data object PostDeleted : PostActionsEffect
}

@HiltViewModel
class PostActionsViewModel @Inject constructor(
    private val submitReportUseCase: SubmitReportUseCase,
    private val deletePostUseCase: DeletePostUseCase,
    private val chatRepository: ChatRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostActionsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<PostActionsEffect>()
    val effect: SharedFlow<PostActionsEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                _uiState.update { it.copy(currentUserId = prefs.userId) }
            }
        }
    }

    fun setActionPost(post: Post) {
        _uiState.update { it.copy(actionPost = post) }
    }

    fun showReportSheet(post: Post) {
        _uiState.update { it.copy(actionPost = post, showReportSheet = true) }
    }

    fun dismissReportSheet() {
        _uiState.update { it.copy(showReportSheet = false) }
    }

    fun showDeleteDialog(post: Post) {
        _uiState.update { it.copy(actionPost = post, showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun submitReport(reason: String, description: String?) {
        val post = _uiState.value.actionPost ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReportSubmitting = true) }
            submitReportUseCase(
                targetType = "post",
                targetId = post.id,
                reason = reason,
                description = description,
            ).onSuccess {
                dismissReportSheet()
                _effect.emit(PostActionsEffect.ShowToast("举报已提交"))
            }.onError { _, msg ->
                _effect.emit(PostActionsEffect.ShowToast(msg ?: "举报提交失败"))
            }
            _uiState.update { it.copy(isReportSubmitting = false) }
        }
    }

    fun deletePost() {
        val post = _uiState.value.actionPost ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            deletePostUseCase(post.id).onSuccess {
                dismissDeleteDialog()
                _effect.emit(PostActionsEffect.ShowToast("已删除"))
                _effect.emit(PostActionsEffect.PostDeleted)
            }.onError { _, msg ->
                _effect.emit(PostActionsEffect.ShowToast(msg ?: "删除失败"))
            }
            _uiState.update { it.copy(isDeleting = false) }
        }
    }

    fun sharePost(post: Post) {
        _uiState.update { it.copy(actionPost = post, showSelectFriend = true) }
        viewModelScope.launch {
            _effect.emit(PostActionsEffect.NavigateToSelectFriend)
        }
    }

    fun editPost(post: Post) {
        viewModelScope.launch {
            _effect.emit(PostActionsEffect.NavigateToEditPost(post.id))
        }
    }

    fun sendSharePost(friendId: Long) {
        val post = _uiState.value.actionPost ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showSelectFriend = false, actionPost = null) }
            chatRepository.sendShareMessage(
                receiverId = friendId,
                shareType = "post",
                shareId = post.id,
                content = post.content,
            ).onSuccess {
                _effect.emit(PostActionsEffect.ShowToast("分享成功"))
            }.onError { _, msg ->
                _effect.emit(PostActionsEffect.ShowToast(msg ?: "分享失败"))
            }
        }
    }

    fun dismissSelectFriend() {
        _uiState.update { it.copy(showSelectFriend = false, actionPost = null) }
    }
}
