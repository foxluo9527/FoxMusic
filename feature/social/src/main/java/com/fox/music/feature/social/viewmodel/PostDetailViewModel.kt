package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.GetPostCommentsPagingUseCase
import com.fox.music.core.domain.usecase.GetPostCommentRepliesPagingUseCase
import com.fox.music.core.domain.usecase.GetPostDetailUseCase
import com.fox.music.core.domain.usecase.PostCommentUseCase
import com.fox.music.core.domain.usecase.TogglePostLikeUseCase
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostDetailState(
    val postId: Long = 0L,
    val post: Post? = null,
    val currentUserId: Long? = null,
    val comments: Flow<PagingData<Comment>> = flowOf(),
    val pendingComments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val isCommentSending: Boolean = false,
    val commentInput: String = "",
    val replyParentId: Long? = null,
    val replyHint: String? = null,
    val expandedCommentIds: Set<Long> = emptySet(),
    val replyFlows: Map<Long, Flow<PagingData<Comment>>> = emptyMap(),
    val isRefreshing: Boolean = false,
    val repliesRefreshTrigger: Int = 0,
    val commentsRefreshTrigger: Int = 0,
) : UiState

sealed interface PostDetailIntent : UiIntent {
    data object Load : PostDetailIntent
    data object Refresh : PostDetailIntent
    data class UpdateCommentInput(val text: String) : PostDetailIntent
    data object SendComment : PostDetailIntent
    data class ReplyToComment(val commentId: Long, val userName: String) : PostDetailIntent
    data object ClearReply : PostDetailIntent
    data class ToggleExpandReplies(val commentId: Long) : PostDetailIntent
    data object ToggleLike : PostDetailIntent
    data object ClearPendingComments : PostDetailIntent
}

sealed interface PostDetailEffect : UiEffect {
    data class ShowToast(val message: String) : PostDetailEffect
    data class CommentSent(val comment: Comment) : PostDetailEffect
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPostDetailUseCase: GetPostDetailUseCase,
    private val getPostCommentsPagingUseCase: GetPostCommentsPagingUseCase,
    private val getPostCommentRepliesPagingUseCase: GetPostCommentRepliesPagingUseCase,
    private val postCommentUseCase: PostCommentUseCase,
    private val togglePostLikeUseCase: TogglePostLikeUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<PostDetailState, PostDetailIntent, PostDetailEffect>(
    PostDetailState(postId = savedStateHandle.get<Long>("postId") ?: 0L),
) {

    init {
        sendIntent(PostDetailIntent.Load)
        viewModelScope.launch {
            savedStateHandle.getStateFlow(POST_UPDATED_KEY, false).collect { updated ->
                if (updated) {
                    savedStateHandle[POST_UPDATED_KEY] = false
                    sendIntent(PostDetailIntent.Refresh)
                }
            }
        }
    }

    override fun handleIntent(intent: PostDetailIntent) {
        when (intent) {
            PostDetailIntent.Load -> load()
            PostDetailIntent.Refresh -> refresh()
            is PostDetailIntent.UpdateCommentInput -> updateState { copy(commentInput = intent.text) }
            PostDetailIntent.SendComment -> sendComment()
            is PostDetailIntent.ReplyToComment -> updateState {
                copy(replyParentId = intent.commentId, replyHint = "回复 @${intent.userName}")
            }
            PostDetailIntent.ClearReply -> updateState { copy(replyParentId = null, replyHint = null) }
            is PostDetailIntent.ToggleExpandReplies -> toggleReplies(intent.commentId)
            PostDetailIntent.ToggleLike -> toggleLike()
            PostDetailIntent.ClearPendingComments -> updateState { copy(pendingComments = emptyList()) }
        }
    }

    fun getRepliesFlow(commentId: Long): Flow<PagingData<Comment>> {
        return currentState.replyFlows[commentId] ?: getPostCommentRepliesPagingUseCase(commentId)
    }

    private fun refresh() {
        viewModelScope.launch {
            updateState { copy(isRefreshing = true) }
            when (val result = getPostDetailUseCase(currentState.postId)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            post = result.data,
                            isRefreshing = false,
                            pendingComments = emptyList(),
                            expandedCommentIds = emptySet(),
                            replyFlows = emptyMap(),
                            comments = getPostCommentsPagingUseCase(currentState.postId)
                                .cachedIn(viewModelScope),
                            commentsRefreshTrigger = commentsRefreshTrigger + 1,
                        )
                    }
                }
                is Result.Error -> {
                    updateState { copy(isRefreshing = false) }
                    sendEffect(PostDetailEffect.ShowToast(result.exception.message ?: "刷新失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val userId = userPreferencesRepository.userPreferences.first().userId
            updateState { copy(currentUserId = userId, isLoading = true) }
            when (val result = getPostDetailUseCase(currentState.postId)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            post = result.data,
                            isLoading = false,
                            comments = getPostCommentsPagingUseCase(currentState.postId)
                                .cachedIn(viewModelScope),
                        )
                    }
                }
                is Result.Error -> {
                    updateState { copy(isLoading = false) }
                    sendEffect(PostDetailEffect.ShowToast(result.exception.message ?: "加载失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun sendComment() {
        val content = currentState.commentInput.trim()
        if (content.isBlank()) return
        viewModelScope.launch {
            updateState { copy(isCommentSending = true) }
            when (
                val result = postCommentUseCase(
                    postId = currentState.postId,
                    content = content,
                    parentId = currentState.replyParentId,
                )
            ) {
                is Result.Success -> {
                    val newComment = result.data
                    val isTopLevel = newComment.parentId == null
                    updateState {
                        copy(
                            commentInput = "",
                            replyParentId = null,
                            replyHint = null,
                            isCommentSending = false,
                            pendingComments = if (isTopLevel) {
                                (listOf(newComment) + pendingComments).distinctBy { it.id }
                            } else {
                                pendingComments
                            },
                            repliesRefreshTrigger = repliesRefreshTrigger + 1,
                            commentsRefreshTrigger = commentsRefreshTrigger + 1,
                            post = post?.copy(commentCount = (post?.commentCount ?: 0) + 1),
                        )
                    }
                    sendEffect(PostDetailEffect.CommentSent(newComment))
                }
                is Result.Error -> {
                    updateState { copy(isCommentSending = false) }
                    sendEffect(PostDetailEffect.ShowToast(result.exception.message ?: "评论失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun toggleReplies(commentId: Long) {
        updateState {
            val expanded = expandedCommentIds.toMutableSet()
            var flows = replyFlows
            if (commentId in expanded) {
                expanded.remove(commentId)
            } else {
                expanded.add(commentId)
                if (commentId !in flows) {
                    flows = flows + (commentId to getPostCommentRepliesPagingUseCase(commentId))
                }
            }
            copy(expandedCommentIds = expanded, replyFlows = flows)
        }
    }

    private fun toggleLike() {
        val post = currentState.post ?: return
        viewModelScope.launch {
            when (togglePostLikeUseCase(post.id)) {
                is Result.Success -> {
                    val newLiked = !post.isLiked
                    updateState {
                        copy(
                            post = post.copy(
                                isLiked = newLiked,
                                likeCount = if (newLiked) post.likeCount + 1
                                else (post.likeCount - 1).coerceAtLeast(0),
                            ),
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    companion object {
        const val POST_UPDATED_KEY = "postUpdated"
    }
}
