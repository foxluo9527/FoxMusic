package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetMusicCommentsPagingUseCase
import com.fox.music.core.domain.usecase.LikeMusicCommentUseCase
import com.fox.music.core.domain.usecase.PostMusicCommentUseCase
import com.fox.music.core.domain.usecase.GetCommentRepliesUseCase
import com.fox.music.core.model.social.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentLikeState(
    val isLiked: Boolean,
    val likeCount: Int,
)

data class MusicCommentDetailState(
    val replyDraft: String = "",
    val expandedComments: Set<Long> = emptySet(),
    val expandedReplies: Map<Long, List<Comment>> = emptyMap(), // commentId -> replies list
    val loadingReplies: Set<Long> = emptySet(), // commentIds being loaded
    val isLoading: Boolean = false,
    val error: String? = null,
    val commentsRefreshTrigger: Int = 0,
    val commentLikes: Map<Long, CommentLikeState> = emptyMap(),
) : UiState

sealed interface MusicCommentDetailIntent : UiIntent {
    data class UpdateDraft(val text: String) : MusicCommentDetailIntent
    data class PostReply(val musicId: Long, val content: String, val parentId: Long?) : MusicCommentDetailIntent
    data class LikeComment(
        val commentId: Long,
        val isLiked: Boolean,
        val likeCount: Int,
    ) : MusicCommentDetailIntent
    data class ToggleExpandReplies(
        val commentId: Long,
        val embeddedReplies: List<Comment> = emptyList(),
        val replyCount: Int = 0,
    ) : MusicCommentDetailIntent
    data class LoadMoreReplies(val musicId: Long, val commentId: Long, val currentCount: Int) : MusicCommentDetailIntent
}

sealed interface MusicCommentDetailEffect : UiEffect {
    data class ShowMessage(val message: String) : MusicCommentDetailEffect
    data object CommentSent : MusicCommentDetailEffect
}

@HiltViewModel
class MusicCommentDetailViewModel @Inject constructor(
    private val getMusicCommentsPagingUseCase: GetMusicCommentsPagingUseCase,
    private val postMusicCommentUseCase: PostMusicCommentUseCase,
    private val likeMusicCommentUseCase: LikeMusicCommentUseCase,
    private val getCommentRepliesUseCase: GetCommentRepliesUseCase
) : MviViewModel<MusicCommentDetailState, MusicCommentDetailIntent, MusicCommentDetailEffect>(MusicCommentDetailState()) {

    private val commentsFlows = mutableMapOf<Long, Flow<PagingData<Comment>>>()

    fun getCommentsPaging(musicId: Long): Flow<PagingData<Comment>> {
        return commentsFlows.getOrPut(musicId) {
            getMusicCommentsPagingUseCase(musicId).cachedIn(viewModelScope)
        }
    }

    override fun handleIntent(intent: MusicCommentDetailIntent) {
        when (intent) {
            is MusicCommentDetailIntent.UpdateDraft -> updateDraft(intent.text)
            is MusicCommentDetailIntent.PostReply -> postReply(intent.musicId, intent.content, intent.parentId)
            is MusicCommentDetailIntent.LikeComment -> likeComment(
                commentId = intent.commentId,
                isLiked = intent.isLiked,
                likeCount = intent.likeCount,
            )
            is MusicCommentDetailIntent.ToggleExpandReplies -> toggleExpandReplies(
                commentId = intent.commentId,
                embeddedReplies = intent.embeddedReplies,
                replyCount = intent.replyCount,
            )
            is MusicCommentDetailIntent.LoadMoreReplies -> loadMoreReplies(intent.commentId, intent.currentCount)
        }
    }

    private fun updateDraft(text: String) {
        updateState { copy(replyDraft = text) }
    }

    private fun toggleExpandReplies(
        commentId: Long,
        embeddedReplies: List<Comment>,
        replyCount: Int,
    ) {
        if (commentId in currentState.expandedComments) {
            updateState { copy(expandedComments = expandedComments - commentId) }
            return
        }

        updateState { copy(expandedComments = expandedComments + commentId) }

        if (!currentState.expandedReplies.containsKey(commentId)) {
            if (embeddedReplies.isNotEmpty()) {
                updateState {
                    copy(expandedReplies = expandedReplies + (commentId to embeddedReplies))
                }
            }
            if (embeddedReplies.size < replyCount) {
                loadMoreReplies(commentId, embeddedReplies.size)
            }
        }
    }

    private fun postReply(musicId: Long, content: String, parentId: Long?) {
        if (content.isBlank()) return
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            when (val res = postMusicCommentUseCase(musicId, content, parentId)) {
                is Result.Success -> {
                    val newComment = res.data
                    val isTopLevel = newComment.parentId == null
                    updateState {
                        copy(
                            replyDraft = "",
                            isLoading = false,
                            error = null,
                            commentsRefreshTrigger = commentsRefreshTrigger + 1,
                        )
                    }
                    if (!isTopLevel && parentId != null) {
                        val existingCount = currentState.expandedReplies[parentId]?.size ?: 0
                        loadMoreReplies(parentId, existingCount)
                    }
                    sendEffect(
                        MusicCommentDetailEffect.ShowMessage(
                            if (isTopLevel) "评论成功" else "回复成功"
                        )
                    )
                    sendEffect(MusicCommentDetailEffect.CommentSent)
                }
                is Result.Error -> {
                    val errorMsg = res.message ?: "发表失败"
                    updateState { copy(isLoading = false, error = errorMsg) }
                    sendEffect(MusicCommentDetailEffect.ShowMessage(errorMsg))
                }

                is Result.Loading -> {
                    updateState { copy(isLoading = true) }
                }
            }
        }
    }

    private fun likeComment(commentId: Long, isLiked: Boolean, likeCount: Int) {
        viewModelScope.launch {
            when (val res = likeMusicCommentUseCase(commentId)) {
                is Result.Success -> {
                    val newLiked = !isLiked
                    val newCount = if (newLiked) likeCount + 1 else (likeCount - 1).coerceAtLeast(0)
                    updateState {
                        copy(
                            commentLikes = commentLikes + (commentId to CommentLikeState(newLiked, newCount)),
                            commentsRefreshTrigger = commentsRefreshTrigger + 1,
                        )
                    }
                    sendEffect(MusicCommentDetailEffect.ShowMessage("点赞成功"))
                }
                is Result.Error -> {
                    val errorMsg = res.message ?: "点赞失败"
                    updateState { copy(error = errorMsg) }
                    sendEffect(MusicCommentDetailEffect.ShowMessage(errorMsg))
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun loadMoreReplies(commentId: Long, currentCount: Int) {
        if (commentId in currentState.loadingReplies) return

        val pageSize = 20
        val newPage = (currentCount / pageSize) + 1

        updateState { copy(loadingReplies = loadingReplies + commentId) }

        viewModelScope.launch {
            when (val res = getCommentRepliesUseCase(commentId, newPage, pageSize)) {
                is Result.Success -> {
                    val newReplies = res.data.list
                    val existing = currentState.expandedReplies[commentId] ?: emptyList()
                    val existingIds = existing.map { it.id }.toSet()
                    val merged = existing + newReplies.filter { it.id !in existingIds }

                    updateState {
                        copy(
                            expandedReplies = expandedReplies + (commentId to merged),
                            loadingReplies = loadingReplies - commentId
                        )
                    }
                }
                is Result.Error -> {
                    updateState { copy(loadingReplies = loadingReplies - commentId) }
                    sendEffect(MusicCommentDetailEffect.ShowMessage(res.message ?: "加载回复失败"))
                }

                else -> {}
            }
        }
    }
}
