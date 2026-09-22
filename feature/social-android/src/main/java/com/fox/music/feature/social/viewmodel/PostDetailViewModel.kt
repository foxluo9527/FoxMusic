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
import com.fox.music.core.domain.usecase.GetPostLikesPagingUseCase
import com.fox.music.core.domain.usecase.PostCommentUseCase
import com.fox.music.core.domain.usecase.DeletePostCommentUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.domain.usecase.TogglePostLikeUseCase
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PostDetailFeedTab {
    COMMENTS,
    LIKES,
}

data class PostDetailState(
    val postId: Long = 0L,
    val post: Post? = null,
    val currentUserId: Long? = null,
    val feedTab: PostDetailFeedTab = PostDetailFeedTab.COMMENTS,
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
    val likesRefreshTrigger: Int = 0,
) : UiState {
    val canViewLikeList: Boolean
        get() = currentUserId != null && post?.userId == currentUserId
}

sealed interface PostDetailIntent : UiIntent {
    data object Load : PostDetailIntent
    data object Refresh : PostDetailIntent
    data class SelectFeedTab(val tab: PostDetailFeedTab) : PostDetailIntent
    data class UpdateCommentInput(val text: String) : PostDetailIntent
    data object SendComment : PostDetailIntent
    data class ReplyToComment(val commentId: Long, val userName: String) : PostDetailIntent
    data object ClearReply : PostDetailIntent
    data class ToggleExpandReplies(val commentId: Long) : PostDetailIntent
    data object ToggleLike : PostDetailIntent
    data object ToggleMusicFavorite : PostDetailIntent
    data object ClearPendingComments : PostDetailIntent
    data class DeleteComment(val commentId: Long) : PostDetailIntent
    data class ReportComment(val commentId: Long, val reason: String, val description: String?) : PostDetailIntent
}

sealed interface PostDetailEffect : UiEffect {
    data class ShowToast(val message: String) : PostDetailEffect
    data class CommentSent(val comment: Comment) : PostDetailEffect
    data object CommentDeleted : PostDetailEffect
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPostDetailUseCase: GetPostDetailUseCase,
    private val getPostCommentsPagingUseCase: GetPostCommentsPagingUseCase,
    private val getPostLikesPagingUseCase: GetPostLikesPagingUseCase,
    private val getPostCommentRepliesPagingUseCase: GetPostCommentRepliesPagingUseCase,
    private val postCommentUseCase: PostCommentUseCase,
    private val togglePostLikeUseCase: TogglePostLikeUseCase,
    private val toggleMusicFavoriteUseCase: ToggleMusicFavoriteUseCase,
    private val deletePostCommentUseCase: DeletePostCommentUseCase,
    private val submitReportUseCase: SubmitReportUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<PostDetailState, PostDetailIntent, PostDetailEffect>(
    PostDetailState(postId = savedStateHandle.get<Long>("postId") ?: 0L),
) {

    private val postId: Long = savedStateHandle.get<Long>("postId") ?: 0L
    val commentsPagingFlow = getPostCommentsPagingUseCase(postId).cachedIn(viewModelScope)
    val likesPagingFlow = getPostLikesPagingUseCase(postId).cachedIn(viewModelScope)

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
            is PostDetailIntent.SelectFeedTab -> {
                if (intent.tab == PostDetailFeedTab.LIKES && !currentState.canViewLikeList) return
                updateState { copy(feedTab = intent.tab) }
            }
            is PostDetailIntent.UpdateCommentInput -> updateState { copy(commentInput = intent.text) }
            PostDetailIntent.SendComment -> sendComment()
            is PostDetailIntent.ReplyToComment -> updateState {
                copy(replyParentId = intent.commentId, replyHint = "回复 @${intent.userName}")
            }
            PostDetailIntent.ClearReply -> updateState { copy(replyParentId = null, replyHint = null) }
            is PostDetailIntent.ToggleExpandReplies -> toggleReplies(intent.commentId)
            PostDetailIntent.ToggleLike -> toggleLike()
            PostDetailIntent.ToggleMusicFavorite -> toggleMusicFavorite()
            PostDetailIntent.ClearPendingComments -> updateState { copy(pendingComments = emptyList()) }
            is PostDetailIntent.DeleteComment -> deleteComment(intent.commentId)
            is PostDetailIntent.ReportComment -> reportComment(intent.commentId, intent.reason, intent.description)
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
                    val newPost = result.data
                    val canViewLikes = currentState.currentUserId != null &&
                        newPost.userId == currentState.currentUserId
                    updateState {
                        copy(
                            post = newPost,
                            isRefreshing = false,
                            pendingComments = emptyList(),
                            expandedCommentIds = emptySet(),
                            replyFlows = emptyMap(),
                            feedTab = if (canViewLikes) feedTab else PostDetailFeedTab.COMMENTS,
                            commentsRefreshTrigger = commentsRefreshTrigger + 1,
                            likesRefreshTrigger = likesRefreshTrigger + 1,
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
                    val newPost = result.data
                    val canViewLikes = userId != null && newPost.userId == userId
                    updateState {
                        copy(
                            post = newPost,
                            isLoading = false,
                            feedTab = if (canViewLikes) feedTab else PostDetailFeedTab.COMMENTS,
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
                            likesRefreshTrigger = likesRefreshTrigger + 1,
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private fun toggleMusicFavorite() {
        val post = currentState.post ?: return
        val music = post.music ?: return
        viewModelScope.launch {
            when (val result = toggleMusicFavoriteUseCase(music.id)) {
                is Result.Success -> {
                    val newFavorite = !music.isFavorite
                    updateState {
                        copy(post = post.copy(music = music.copy(isFavorite = newFavorite)))
                    }
                    sendEffect(
                        PostDetailEffect.ShowToast(
                            if (newFavorite) "已收藏音乐" else "已取消收藏音乐",
                        ),
                    )
                }
                is Result.Error -> sendEffect(
                    PostDetailEffect.ShowToast(result.exception.message ?: "操作失败"),
                )
                else -> Unit
            }
        }
    }

    private fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            deletePostCommentUseCase(commentId).onSuccess {
                sendEffect(PostDetailEffect.ShowToast("评论已删除"))
                sendEffect(PostDetailEffect.CommentDeleted)
                refresh()
            }.onError { _, msg ->
                sendEffect(PostDetailEffect.ShowToast(msg ?: "删除失败"))
            }
        }
    }

    private fun reportComment(commentId: Long, reason: String, description: String?) {
        viewModelScope.launch {
            submitReportUseCase(
                targetType = "comment",
                targetId = commentId,
                reason = reason,
                description = description,
            ).onSuccess {
                sendEffect(PostDetailEffect.ShowToast("举报已提交"))
            }.onError { _, msg ->
                sendEffect(PostDetailEffect.ShowToast(msg ?: "举报提交失败"))
            }
        }
    }

    companion object {
        const val POST_UPDATED_KEY = "postUpdated"
    }
}
