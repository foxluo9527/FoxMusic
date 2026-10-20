package com.fox.music.feature.social

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 社区广场的跨端契约（State / Intent / Effect）。
 *
 * 与首页 HomeContract 同一套路：Android 侧 [:feature:social-android] 的
 * CommunityPlazaViewModel 用 Hilt 生产 state，iOS 侧由 iosShared 的
 * Presenter 生产，两边喂给同一个 [com.fox.music.feature.social.CommunityPlazaContent]。
 */
enum class PlazaLayoutMode {
    STAGGERED,
    LIST,
}

fun PlazaLayoutMode.toStoredValue(): String = when (this) {
    PlazaLayoutMode.STAGGERED -> "staggered"
    PlazaLayoutMode.LIST -> "list"
}

fun String.toPlazaLayoutMode(): PlazaLayoutMode = when (this) {
    "list" -> PlazaLayoutMode.LIST
    else -> PlazaLayoutMode.STAGGERED
}

data class CommunityPlazaState(
    val posts: Flow<PagingData<Post>> = flowOf(),
    val layoutMode: PlazaLayoutMode = PlazaLayoutMode.STAGGERED,
    val isRefreshing: Boolean = false,
    /** 点赞乐观更新覆盖表：postId → (是否点赞, 点赞数) */
    val likeOverrides: Map<Long, Pair<Boolean, Int>> = emptyMap(),
    val commentPreviews: Map<Long, Comment?> = emptyMap(),
    val commentingPostId: Long? = null,
    val commentInput: String = "",
    val isCommentSending: Boolean = false,
    val filterTag: String? = null,
) : UiState

/** 把点赞覆盖表应用到帖子（列表在分页数据里，无法就地改，故渲染时叠加）。 */
fun CommunityPlazaState.applyLikeOverride(post: Post): Post {
    likeOverrides[post.id]?.let { (liked, count) ->
        return post.copy(isLiked = liked, likeCount = count)
    }
    return post
}

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
