package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.usecase.GetPostCommentPreviewUseCase
import com.fox.music.core.domain.usecase.GetPostsPagingUseCase
import com.fox.music.core.domain.usecase.PostCommentUseCase
import com.fox.music.core.domain.usecase.TogglePostLikeUseCase
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlazaLayoutMode {
    STAGGERED,
    LIST,
}

private fun PlazaLayoutMode.toStoredValue(): String = when (this) {
    PlazaLayoutMode.STAGGERED -> "staggered"
    PlazaLayoutMode.LIST -> "list"
}

private fun String.toPlazaLayoutMode(): PlazaLayoutMode = when (this) {
    "list" -> PlazaLayoutMode.LIST
    else -> PlazaLayoutMode.STAGGERED
}

data class CommunityPlazaState(
    val posts: Flow<PagingData<Post>> = flowOf(),
    val layoutMode: PlazaLayoutMode = PlazaLayoutMode.STAGGERED,
    val isRefreshing: Boolean = false,
    val likeOverrides: Map<Long, Pair<Boolean, Int>> = emptyMap(),
    val commentPreviews: Map<Long, Comment?> = emptyMap(),
    val commentingPostId: Long? = null,
    val commentInput: String = "",
    val isCommentSending: Boolean = false,
    val filterTag: String? = null,
) : UiState

sealed interface CommunityPlazaIntent : UiIntent {
    data object Load : CommunityPlazaIntent
    data object Refresh : CommunityPlazaIntent
    data class OnPostLikeClick(val post: Post) : CommunityPlazaIntent
    data class LoadCommentPreview(val postId: Long) : CommunityPlazaIntent
    data class StartComment(val postId: Long) : CommunityPlazaIntent
    data object DismissComment : CommunityPlazaIntent
    data class UpdateCommentInput(val text: String) : CommunityPlazaIntent
    data object SendComment : CommunityPlazaIntent
    data class FilterByTag(val tag: String?) : CommunityPlazaIntent
    data object ToggleLayoutMode : CommunityPlazaIntent
}

sealed interface CommunityPlazaEffect : UiEffect {
    data object NavigateToCreatePost : CommunityPlazaEffect
    data object NavigateToPublishQueue : CommunityPlazaEffect
    data class NavigateToPostDetail(val postId: Long) : CommunityPlazaEffect
    data class ShowToast(val message: String) : CommunityPlazaEffect
}

@HiltViewModel
class CommunityPlazaViewModel @Inject constructor(
    private val getPostsPagingUseCase: GetPostsPagingUseCase,
    private val togglePostLikeUseCase: TogglePostLikeUseCase,
    private val getPostCommentPreviewUseCase: GetPostCommentPreviewUseCase,
    private val postCommentUseCase: PostCommentUseCase,
    private val preferencesDataStore: FoxPreferencesDataStore,
) : MviViewModel<CommunityPlazaState, CommunityPlazaIntent, CommunityPlazaEffect>(CommunityPlazaState()) {

    init {
        sendIntent(CommunityPlazaIntent.Load)
        viewModelScope.launch {
            preferencesDataStore.plazaLayoutMode.collect { stored ->
                updateState { copy(layoutMode = stored.toPlazaLayoutMode()) }
            }
        }
    }

    override fun handleIntent(intent: CommunityPlazaIntent) {
        when (intent) {
            CommunityPlazaIntent.Load -> loadPosts()
            CommunityPlazaIntent.Refresh -> updateState {
                copy(isRefreshing = true, likeOverrides = emptyMap())
            }
            is CommunityPlazaIntent.OnPostLikeClick -> toggleLike(intent.post)
            is CommunityPlazaIntent.LoadCommentPreview -> loadCommentPreview(intent.postId)
            is CommunityPlazaIntent.StartComment -> updateState {
                copy(commentingPostId = intent.postId, commentInput = "")
            }
            CommunityPlazaIntent.DismissComment -> updateState {
                copy(commentingPostId = null, commentInput = "")
            }
            is CommunityPlazaIntent.UpdateCommentInput -> updateState { copy(commentInput = intent.text) }
            CommunityPlazaIntent.SendComment -> sendComment()
            is CommunityPlazaIntent.FilterByTag -> {
                updateState { copy(filterTag = intent.tag, likeOverrides = emptyMap()) }
                loadPosts()
            }
            CommunityPlazaIntent.ToggleLayoutMode -> toggleLayoutMode()
        }
    }

    private fun toggleLayoutMode() {
        val next = when (currentState.layoutMode) {
            PlazaLayoutMode.STAGGERED -> PlazaLayoutMode.LIST
            PlazaLayoutMode.LIST -> PlazaLayoutMode.STAGGERED
        }
        viewModelScope.launch {
            preferencesDataStore.updatePlazaLayoutMode(next.toStoredValue())
        }
    }

    private var lastHandledRefreshTrigger = 0

    fun onCreatePostClick() {
        sendEffect(CommunityPlazaEffect.NavigateToCreatePost)
    }

    fun onPublishQueueClick() {
        sendEffect(CommunityPlazaEffect.NavigateToPublishQueue)
    }

    fun handleExternalRefreshTrigger(trigger: Int, onRefreshPaging: () -> Unit) {
        if (trigger <= lastHandledRefreshTrigger) return
        lastHandledRefreshTrigger = trigger
        sendIntent(CommunityPlazaIntent.Refresh)
        onRefreshPaging()
    }

    fun applyOverrides(post: Post): Post {
        currentState.likeOverrides[post.id]?.let { (liked, count) ->
            return post.copy(isLiked = liked, likeCount = count)
        }
        return post
    }

    private fun loadPosts() {
        val tag = currentState.filterTag
        updateState {
            copy(posts = getPostsPagingUseCase(tag = tag).flow.cachedIn(viewModelScope))
        }
    }

    private fun toggleLike(post: Post) {
        viewModelScope.launch {
            when (togglePostLikeUseCase(post.id)) {
                is Result.Success -> {
                    val display = applyOverrides(post)
                    val newLiked = !display.isLiked
                    val newCount = if (newLiked) display.likeCount + 1 else (display.likeCount - 1).coerceAtLeast(0)
                    updateState { copy(likeOverrides = likeOverrides + (post.id to (newLiked to newCount))) }
                }
                else -> Unit
            }
        }
    }

    private fun loadCommentPreview(postId: Long) {
        if (currentState.commentPreviews.containsKey(postId)) return
        viewModelScope.launch {
            when (val result = getPostCommentPreviewUseCase(postId)) {
                is Result.Success -> updateState {
                    copy(commentPreviews = commentPreviews + (postId to result.data))
                }
                else -> Unit
            }
        }
    }

    private fun sendComment() {
        val postId = currentState.commentingPostId ?: return
        val content = currentState.commentInput.trim()
        if (content.isBlank()) return
        viewModelScope.launch {
            updateState { copy(isCommentSending = true) }
            when (val result = postCommentUseCase(postId, content)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            isCommentSending = false,
                            commentInput = "",
                            commentingPostId = null,
                            commentPreviews = commentPreviews - postId,
                        )
                    }
                    loadCommentPreview(postId)
                    sendEffect(CommunityPlazaEffect.ShowToast("评论成功"))
                }
                is Result.Error -> {
                    updateState { copy(isCommentSending = false) }
                    sendEffect(CommunityPlazaEffect.ShowToast(result.exception.message ?: "评论失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }
}
