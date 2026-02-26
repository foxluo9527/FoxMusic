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
import com.fox.music.core.model.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicCommentDetailState(
    val replyDraft: String = "",
    val expandedComments: Set<Long> = emptySet(),
    val expandedReplies: Map<Long, List<Comment>> = emptyMap(), // commentId -> replies list
    val loadingReplies: Set<Long> = emptySet(), // commentIds being loaded
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface MusicCommentDetailIntent : UiIntent {
    data class UpdateDraft(val text: String) : MusicCommentDetailIntent
    data class PostReply(val musicId: Long, val content: String, val parentId: Long) : MusicCommentDetailIntent
    data class LikeComment(val commentId: Long) : MusicCommentDetailIntent
    data class ToggleExpandReplies(val commentId: Long) : MusicCommentDetailIntent
    data class LoadMoreReplies(val musicId: Long, val commentId: Long, val currentCount: Int) : MusicCommentDetailIntent
}

sealed interface MusicCommentDetailEffect : UiEffect {
    data class ShowMessage(val message: String) : MusicCommentDetailEffect
}

@HiltViewModel
class MusicCommentDetailViewModel @Inject constructor(
    private val getMusicCommentsPagingUseCase: GetMusicCommentsPagingUseCase,
    private val postMusicCommentUseCase: PostMusicCommentUseCase,
    private val likeMusicCommentUseCase: LikeMusicCommentUseCase,
    private val getCommentRepliesUseCase: GetCommentRepliesUseCase
) : MviViewModel<MusicCommentDetailState, MusicCommentDetailIntent, MusicCommentDetailEffect>(MusicCommentDetailState()) {

    fun getCommentsPaging(musicId: Long): Flow<PagingData<Comment>> {
        return getMusicCommentsPagingUseCase(musicId).cachedIn(viewModelScope)
    }

    override fun handleIntent(intent: MusicCommentDetailIntent) {
        when (intent) {
            is MusicCommentDetailIntent.UpdateDraft -> updateDraft(intent.text)
            is MusicCommentDetailIntent.PostReply -> postReply(intent.musicId, intent.content, intent.parentId)
            is MusicCommentDetailIntent.LikeComment -> likeComment(intent.commentId)
            is MusicCommentDetailIntent.ToggleExpandReplies -> toggleExpandReplies(intent.commentId)
            is MusicCommentDetailIntent.LoadMoreReplies -> loadMoreReplies(intent.commentId, intent.currentCount)
        }
    }

    private fun updateDraft(text: String) {
        updateState { copy(replyDraft = text) }
    }

    private fun toggleExpandReplies(commentId: Long) {
        updateState {
            val newExpanded = expandedComments.toMutableSet()
            val shouldLoad = !newExpanded.contains(commentId)

            if (shouldLoad) {
                newExpanded.add(commentId)
                // 第一次展开时，初始化加载回复
                if (!expandedReplies.containsKey(commentId)) {
                    loadMoreReplies(commentId, 0)
                }
            } else {
                newExpanded.remove(commentId)
            }
            copy(expandedComments = newExpanded)
        }
    }

    private fun postReply(musicId: Long, content: String, parentId: Long) {
        if (content.isBlank()) return
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            when (val res = postMusicCommentUseCase(musicId, content, parentId)) {
                is Result.Success -> {
                    updateState { copy(replyDraft = "", isLoading = false, error = null) }
                    sendEffect(MusicCommentDetailEffect.ShowMessage("回复成功"))
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

    private fun likeComment(commentId: Long) {
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            when (val res = likeMusicCommentUseCase(commentId)) {
                is Result.Success -> {
                    updateState { copy(isLoading = false, error = null) }
                    sendEffect(MusicCommentDetailEffect.ShowMessage("点赞成功"))
                }
                is Result.Error -> {
                    val errorMsg = res.message ?: "点赞失败"
                    updateState { copy(isLoading = false, error = errorMsg) }
                    sendEffect(MusicCommentDetailEffect.ShowMessage(errorMsg))
                }

                is Result.Loading -> {
                    updateState { copy(isLoading = true) }
                }
            }
        }
    }

    private fun loadMoreReplies(commentId: Long, currentCount: Int) {
        val newPage = (currentCount / 5) + 1

        updateState { copy(loadingReplies = loadingReplies + commentId) }

        viewModelScope.launch {
            when (val res = getCommentRepliesUseCase(commentId, newPage, if (newPage == 1) 6 else 5)) {
                is Result.Success -> {
                    val newReplies = res.data.list
                    val finalReplies = if (newPage == 1 && newReplies.isNotEmpty()) {
                        // 第一页时去掉第一条（因为可能与主评论重复）
                        newReplies.drop(1)
                    } else if (newPage == 1) {
                        newReplies
                    } else {
                        // 后续页面直接追加到已有的回复后面
                        (currentState.expandedReplies[commentId] ?: emptyList()) + newReplies
                    }

                    updateState {
                        copy(
                            expandedReplies = expandedReplies + (commentId to finalReplies),
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
