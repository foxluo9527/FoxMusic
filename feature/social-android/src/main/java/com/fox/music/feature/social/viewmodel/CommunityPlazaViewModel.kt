package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.usecase.GetPostCommentPreviewUseCase
import com.fox.music.core.domain.usecase.GetPostsPagingUseCase
import com.fox.music.core.domain.usecase.PostCommentUseCase
import com.fox.music.core.domain.usecase.TogglePostLikeUseCase
import com.fox.music.core.model.social.Post
import com.fox.music.feature.social.CommunityPlazaEffect
import com.fox.music.feature.social.CommunityPlazaIntent
import com.fox.music.feature.social.CommunityPlazaState
import com.fox.music.feature.social.PlazaLayoutMode
import com.fox.music.feature.social.applyLikeOverride
import com.fox.music.feature.social.toPlazaLayoutMode
import com.fox.music.feature.social.toStoredValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 社区广场 ViewModel。
 *
 * State / Intent / Effect 的定义在 [:feature:social] commonMain 的
 * CommunityPlazaContract（iOS 端用同一份契约），这里只负责用 Hilt 注入的
 * use case 生产状态，UI 由 CommunityPlazaContent 渲染。
 */
@HiltViewModel
class CommunityPlazaViewModel @Inject constructor(
    private val getPostsPagingUseCase: GetPostsPagingUseCase,
    private val togglePostLikeUseCase: TogglePostLikeUseCase,
    private val getPostCommentPreviewUseCase: GetPostCommentPreviewUseCase,
    private val postCommentUseCase: PostCommentUseCase,
    private val preferencesDataStore: FoxPreferencesDataStore,
) : MviViewModel<CommunityPlazaState, CommunityPlazaIntent, CommunityPlazaEffect>(CommunityPlazaState()) {

    init {
        // 先注册快照的磁盘编解码器，随后的 read 才能在冷启动时解析上一进程落盘的快照
        registerCommunityPageCacheCodec()
        sendIntent(CommunityPlazaIntent.Load)
        viewModelScope.launch {
            preferencesDataStore.plazaLayoutMode.collect { stored ->
                updateState { copy(layoutMode = stored.toPlazaLayoutMode()) }
            }
        }
    }

    /**
     * 分页首屏到手 → 落快照：下次进社区页先渲染这批帖子（分页请求回来后替换）。
     * 只留前 [PLAZA_SNAPSHOT_POST_COUNT] 条，够铺满一屏即可。
     */
    fun onPostsLoaded(posts: List<Post>) {
        if (posts.isEmpty()) return
        // 带 tag 的筛选结果不进快照：否则回到无筛选的广场会先看到别的 tag 下的帖子
        if (currentState.filterTag != null) return
        // 快照只取前若干条：首屏回来后内容不再变化。滚动触发 append 时本方法也会进来，
        // 内容没变就直接返回，避免反复写盘。
        val snapshotPosts = posts.take(PLAZA_SNAPSHOT_POST_COUNT)
        val ids = snapshotPosts.map { it.id }
        if (ids == lastSnapshotPostIds) return
        lastSnapshotPostIds = ids
        TabPageCache.put(
            key = TabPageKeys.COMMUNITY,
            value = CommunityPageSnapshot(posts = snapshotPosts),
        )
    }

    private companion object {
        const val PLAZA_SNAPSHOT_POST_COUNT = 20
    }

    /** 上一次落盘快照的帖子 id 序列，用于跳过重复写入 */
    private var lastSnapshotPostIds: List<Long>? = null

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

    /** 渲染侧的点赞叠加（与 iOS Presenter 同一份实现：applyLikeOverride）。 */
    fun applyOverrides(post: Post): Post = currentState.applyLikeOverride(post)

    private fun loadPosts() {
        val tag = currentState.filterTag
        // 缓存优先：先把上一次的帖子铺上（无 tag 过滤时才有意义，带 tag 的快照不复用），
        // 分页首屏回来后自动替换，切回社区 Tab 不会先空一屏。
        val cached = TabPageCache.read<CommunityPageSnapshot>(TabPageKeys.COMMUNITY)
            ?.takeIf { currentState.filterTag == null }
        updateState {
            copy(
                cachedPosts = cached?.posts.orEmpty(),
                posts = getPostsPagingUseCase(tag = tag).flow.cachedIn(viewModelScope),
            )
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
