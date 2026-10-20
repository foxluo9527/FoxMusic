package com.fox.music.feature.social

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import kotlinx.coroutines.flow.Flow

/** 帖子详情的跨端契约（State / Intent / Effect）。 */
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
    /** 待举报的评论 ID（长按评论点举报后置位，用于理由选择弹窗提交） */
    val reportCommentId: Long? = null,
    val showCommentReportSheet: Boolean = false,
    val isReportSubmitting: Boolean = false,
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
    /** 长按评论点举报：打开理由选择弹窗 */
    data class ReportComment(val commentId: Long) : PostDetailIntent
    /** 举报弹窗内确认提交 */
    data class SubmitCommentReport(
        val reason: String,
        val description: String?,
    ) : PostDetailIntent
}

sealed interface PostDetailEffect : UiEffect {
    data class ShowToast(val message: String) : PostDetailEffect
    data class CommentSent(val comment: Comment) : PostDetailEffect
    data object CommentDeleted : PostDetailEffect
}
