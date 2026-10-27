package com.fox.music.iosshared.ui

import kotlinx.coroutines.MainScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.data.mapper.toComment
import com.fox.music.core.data.mapper.toPost
import com.fox.music.core.data.mapper.toPostLikerUser
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.user.User
import com.fox.music.core.network.model.PostDto
import com.fox.music.feature.social.CommunityPlazaContent
import com.fox.music.feature.social.CommunityPlazaIntent
import com.fox.music.feature.social.CommunityPlazaState
import com.fox.music.feature.social.PlazaLayoutMode
import com.fox.music.feature.social.PostDetailContent
import com.fox.music.feature.social.PostDetailFeedTab
import com.fox.music.feature.social.PostDetailIntent
import com.fox.music.feature.social.PostDetailState
import com.fox.music.feature.social.applyLikeOverride
import com.fox.music.iosshared.FoxFirstPageCache
import com.fox.music.iosshared.FoxSceneCache
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.CoroutineScope
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** 帖子列表分页源：GET api/posts（noAuth）。keyword 用于搜索页帖子 Tab，authorId 用于个人主页动态 Tab。 */
internal class PostPagingSource(
    private val api: FoxMusicApi,
    private val sort: String = "latest",
    private val tag: String? = null,
    private val keyword: String? = null,
    private val authorId: Long? = null,
    /** 冷启动缓存守卫：首次命中缓存后置 true，保证后续 refresh 走网络而非再次命中缓存（避免死循环）。 */
    private val isCacheConsumed: () -> Boolean = { false },
    private val markCacheConsumed: () -> Unit = {},
    /** 命中缓存后回调：由 Presenter 在后台触发一次 refresh 拉取最新（stale-while-revalidate）。 */
    private val onCacheServed: (() -> Unit)? = null,
) : PagingSource<Int, Post>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Post> = try {
        val page = params.key ?: 1
        // 冷启动缓存**只属于社区广场无条件首页**：他人主页（authorId）/标签（tag）/
        // 搜索（keyword）都不能读写 __posts_* 快照，否则会把广场动态错当某人主页动态
        // （线上 bug：查看任何人主页动态都显示当前用户的），且过滤结果会反过来污染广场缓存。
        val isPlazaFirstPage = authorId == null && tag.isNullOrBlank() && keyword.isNullOrBlank()
        // 冷启动：社区广场帖子第一页先命中磁盘快照秒出，之后后台 refresh 再走网络拉最新
        if (page == 1 && isPlazaFirstPage && !isCacheConsumed()) {
            val key = "${FoxFirstPageCache.ownerPrefix()}__posts_${sort}_v1"
            FoxFirstPageCache.load(key, kotlinx.serialization.builtins.ListSerializer(PostDto.serializer()))?.let { cached ->
                markCacheConsumed()
                onCacheServed?.invoke()
                val list = cached.map { it.toPost() }
                return LoadResult.Page(
                    data = list,
                    prevKey = null,
                    nextKey = if (list.size < params.loadSize) null else 2,
                )
            }
        }
        val response = api.fetchPostPage(page, params.loadSize, sort, tag, keyword, authorId)
        if (page == 1 && isPlazaFirstPage) {
            FoxFirstPageCache.save(
                "${FoxFirstPageCache.ownerPrefix()}__posts_${sort}_v1",
                kotlinx.serialization.builtins.ListSerializer(PostDto.serializer()),
                response.list,
            )
        }
        val list = response.list.map { it.toPost() }
        val loaded = (page - 1) * params.loadSize + list.size
        val hasMore = if (response.totalPages > 0) {
            page < response.totalPages
        } else {
            list.isNotEmpty() && loaded < response.total && list.size >= params.loadSize
        }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (hasMore) page + 1 else null,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? = null
}

/** 帖子一级评论分页源：GET api/posts/{id}/comments。 */
internal class PostCommentPagingSource(
    private val api: FoxMusicApi,
    private val postId: Long,
) : PagingSource<Int, Comment>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Comment> = try {
        val page = params.key ?: 1
        val response = api.fetchPostComments(postId, page, params.loadSize)
        val list = response.list.map { it.toComment() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Comment>): Int? = null
}

/** 评论回复分页源：GET api/posts/comments/{id}/replies。 */
internal class PostCommentRepliesPagingSource(
    private val api: FoxMusicApi,
    private val commentId: Long,
) : PagingSource<Int, Comment>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Comment> = try {
        val page = params.key ?: 1
        val response = api.fetchPostCommentReplies(commentId, page, params.loadSize)
        val list = response.list.map { it.toComment() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Comment>): Int? = null
}

/** 点赞者分页源：GET api/posts/{id}/likes。 */
internal class PostLikesPagingSource(
    private val api: FoxMusicApi,
    private val postId: Long,
) : PagingSource<Int, User>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> = try {
        val page = params.key ?: 1
        val response = api.fetchPostLikes(postId, page, params.loadSize)
        val list = response.list.map { it.toPostLikerUser() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? = null
}

/**
 * iOS 端社区广场 Presenter：复用 feature:social commonMain 的 CommunityPlazaContent，
 * 状态机逐条对齐 Android CommunityPlazaViewModel（差异：布局偏好不落 DataStore，
 * 进程内保存 —— core:datastore 是 android.library，iOS 侧暂无持久化）。
 */
internal class CommunityPlazaPresenter(
    private val api: FoxMusicApi,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
) {

    private val _state = MutableStateFlow(CommunityPlazaState())
    val state: StateFlow<CommunityPlazaState> = _state.asStateFlow()

    /**
     * 冷启动第一页缓存守卫：命中一次后置 true，保证随后的 refresh（reload）走网络而非
     * 再次命中磁盘缓存（否则会永远显示旧帖子）。进程内有效，离开广场再回来会被重置。
     */
    private var plazaCacheServed = false

    init {
        reload()
    }

    fun handle(intent: CommunityPlazaIntent) {
        when (intent) {
            CommunityPlazaIntent.Load -> reload()
            CommunityPlazaIntent.Refresh -> {
                _state.value = _state.value.copy(isRefreshing = true, likeOverrides = emptyMap())
                reload()
                scope.launch { _state.value = _state.value.copy(isRefreshing = false) }
            }
            is CommunityPlazaIntent.OnPostLikeClick -> toggleLike(intent.post)
            is CommunityPlazaIntent.LoadCommentPreview -> loadCommentPreview(intent.postId)
            is CommunityPlazaIntent.StartComment -> _state.value = _state.value.copy(
                commentingPostId = intent.postId,
                commentInput = "",
            )
            CommunityPlazaIntent.DismissComment -> _state.value = _state.value.copy(
                commentingPostId = null,
                commentInput = "",
            )
            is CommunityPlazaIntent.UpdateCommentInput ->
                _state.value = _state.value.copy(commentInput = intent.text)
            CommunityPlazaIntent.SendComment -> sendComment()
            is CommunityPlazaIntent.FilterByTag -> {
                _state.value = _state.value.copy(
                    filterTag = intent.tag,
                    likeOverrides = emptyMap(),
                )
                reload()
            }
            CommunityPlazaIntent.ToggleLayoutMode -> _state.value = _state.value.copy(
                layoutMode = when (_state.value.layoutMode) {
                    PlazaLayoutMode.STAGGERED -> PlazaLayoutMode.LIST
                    PlazaLayoutMode.LIST -> PlazaLayoutMode.STAGGERED
                },
            )
        }
    }

    /** 重建 Pager 拉最新（下拉刷新 / 帖子编辑保存后由 watchAny 回调触发）。 */
    fun reload() {
        val tag = _state.value.filterTag
        _state.value = _state.value.copy(
            posts = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                PostPagingSource(
                    api,
                    sort = "latest",
                    tag = tag,
                    isCacheConsumed = { plazaCacheServed },
                    markCacheConsumed = { plazaCacheServed = true },
                    // 命中缓存秒出后，后台延迟一小会儿再拉最新（stale-while-revalidate）
                    onCacheServed = {
                        scope.launch {
                            delay(800)
                            reload()
                        }
                    },
                )
            }.flow.cachedIn(scope),
        )
    }

    private fun toggleLike(post: Post) {
        scope.launch {
            runCatching { api.togglePostLike(post.id) }.onSuccess {
                val display = _state.value.applyLikeOverride(post)
                val newLiked = !display.isLiked
                val newCount = if (newLiked) {
                    display.likeCount + 1
                } else {
                    (display.likeCount - 1).coerceAtLeast(0)
                }
                _state.value = _state.value.copy(
                    likeOverrides = _state.value.likeOverrides + (post.id to (newLiked to newCount)),
                )
            }.onFailure { onToast(it.message ?: "操作失败") }
        }
    }

    /** 删除自己的帖子：DELETE api/posts/{id} 成功后重载列表（对齐 Android 广场卡片删除）。 */
    fun deletePost(post: Post) {
        scope.launch {
            runCatching { api.deletePost(post.id) }
                .onSuccess {
                    onToast("已删除")
                    reload()
                }
                .onFailure { onToast(it.message ?: "删除失败") }
        }
    }

    private fun loadCommentPreview(postId: Long) {
        if (_state.value.commentPreviews.containsKey(postId)) return
        scope.launch {
            val preview = runCatching { api.fetchPostComments(postId, 1, 1) }
                .getOrNull()?.list?.firstOrNull()?.toComment()
            _state.value = _state.value.copy(
                commentPreviews = _state.value.commentPreviews + (postId to preview),
            )
        }
    }

    private fun sendComment() {
        val postId = _state.value.commentingPostId ?: return
        val content = _state.value.commentInput.trim()
        if (content.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isCommentSending = true)
            runCatching { api.postComment(postId, content) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        isCommentSending = false,
                        commentInput = "",
                        commentingPostId = null,
                        commentPreviews = _state.value.commentPreviews - postId,
                    )
                    loadCommentPreview(postId)
                    onToast("评论成功")
                }
                .onFailure {
                    _state.value = _state.value.copy(isCommentSending = false)
                    onToast(it.message ?: "评论失败")
                }
        }
    }
}

/**
 * iOS 端帖子详情 Presenter：状态机逐条对齐 Android PostDetailViewModel。
 * currentUserId 用 0 表示未登录（Swift 侧不好传 nullable Long）。
 */
internal class PostDetailPresenter(
    private val postId: Long,
    private val api: FoxMusicApi,
    private val scope: CoroutineScope,
    private val currentUserId: Long,
    private val onToast: (String) -> Unit,
    private val onCommentDeleted: () -> Unit,
    /** 评论举报出口：交给 Swift 弹统一举报弹窗（理由/说明由弹窗收集后直接提交）。 */
    private val onReportComment: (Long) -> Unit,
    /** 删除当前帖子成功后：Swift 关闭详情页返回上一级。 */
    private val onPostDeleted: () -> Unit,
) {

    private val _state = MutableStateFlow(PostDetailState(postId = postId, currentUserId = currentUserId))
    val state: StateFlow<PostDetailState> = _state.asStateFlow()

    val commentsFlow: Flow<PagingData<Comment>> = Pager(
        PagingConfig(pageSize = 20, initialLoadSize = 20),
    ) { PostCommentPagingSource(api, postId) }.flow.cachedIn(scope)

    val likesFlow: Flow<PagingData<User>> = Pager(
        PagingConfig(pageSize = 20, initialLoadSize = 20),
    ) { PostLikesPagingSource(api, postId) }.flow.cachedIn(scope)

    private val replyFlows = HashMap<Long, Flow<PagingData<Comment>>>()

    init {
        load()
    }

    fun repliesFlow(commentId: Long): Flow<PagingData<Comment>> =
        replyFlows.getOrPut(commentId) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                PostCommentRepliesPagingSource(api, commentId)
            }.flow.cachedIn(scope)
        }

    fun handle(intent: PostDetailIntent) {
        when (intent) {
            PostDetailIntent.Load -> load()
            PostDetailIntent.Refresh -> refresh()
            is PostDetailIntent.SelectFeedTab -> {
                if (intent.tab == PostDetailFeedTab.LIKES && !_state.value.canViewLikeList) return
                _state.value = _state.value.copy(feedTab = intent.tab)
            }
            is PostDetailIntent.UpdateCommentInput ->
                _state.value = _state.value.copy(commentInput = intent.text)
            PostDetailIntent.SendComment -> sendComment()
            is PostDetailIntent.ReplyToComment -> _state.value = _state.value.copy(
                replyParentId = intent.commentId,
                replyHint = "回复 @${intent.userName}",
            )
            PostDetailIntent.ClearReply ->
                _state.value = _state.value.copy(replyParentId = null, replyHint = null)
            is PostDetailIntent.ToggleExpandReplies -> toggleReplies(intent.commentId)
            PostDetailIntent.ToggleLike -> toggleLike()
            PostDetailIntent.ToggleMusicFavorite -> onToast("请到播放页收藏音乐")
            PostDetailIntent.ClearPendingComments ->
                _state.value = _state.value.copy(pendingComments = emptyList())
            is PostDetailIntent.DeleteComment -> deleteComment(intent.commentId)
            is PostDetailIntent.ReportComment -> onReportComment(intent.commentId)
            // iOS 端评论举报的「理由收集 + 提交」由 Swift 侧 ReportSheet 直接完成：
            // onReportComment(commentId) 上浮到 Swift 后弹原生举报弹窗并自行 submitReport。
            // 共享 PostDetailContent 在 iOS 不会派发 SubmitCommentReport，此处仅保留分支
            // 以满足密封接口穷尽性（对应 Android 走 ViewModel 内部 reportComment）。
            is PostDetailIntent.SubmitCommentReport -> Unit
        }
    }

    /** 删除当前帖子：成功后回调 Swift 关闭详情页（对齐 Android 详情页删除）。 */
    fun deleteCurrentPost() {
        scope.launch {
            runCatching { api.deletePost(postId) }
                .onSuccess {
                    onToast("已删除")
                    onPostDeleted()
                }
                .onFailure { onToast(it.message ?: "删除失败") }
        }
    }

    private fun load() {
        scope.launch {
            val cached = loadCachedPost()
            if (cached != null) {
                _state.value = _state.value.copy(
                    post = cached,
                    isLoading = false,
                    feedTab = PostDetailFeedTab.COMMENTS,
                )
            } else {
                _state.value = _state.value.copy(isLoading = true)
            }
            val post = runCatching { api.fetchPostDetail(postId) }.getOrNull()?.toPost()
            if (post != null) {
                _state.value = _state.value.copy(
                    post = post,
                    isLoading = false,
                    feedTab = PostDetailFeedTab.COMMENTS,
                )
                savePost(post)
            } else {
                _state.value = _state.value.copy(isLoading = false)
                if (cached == null && _state.value.post == null) onToast("帖子不存在")
            }
        }
    }

    /** 拉最新详情（下拉刷新 / 帖子编辑保存后由 watchPost 回调触发）。 */
    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            val post = runCatching { api.fetchPostDetail(postId) }.getOrNull()?.toPost()
            _state.value = _state.value.copy(
                post = post ?: _state.value.post,
                isRefreshing = false,
                pendingComments = emptyList(),
                expandedCommentIds = emptySet(),
                commentsRefreshTrigger = _state.value.commentsRefreshTrigger + 1,
                likesRefreshTrigger = _state.value.likesRefreshTrigger + 1,
            )
            post?.let { savePost(it) }
        }
    }

    private suspend fun loadCachedPost(): Post? =
        FoxSceneCache.load(Post.serializer(), "post", postId.toString())

    private suspend fun savePost(post: Post) {
        FoxSceneCache.save(Post.serializer(), post, "post", postId.toString())
    }

    private fun sendComment() {
        val content = _state.value.commentInput.trim()
        if (content.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isCommentSending = true)
            runCatching { api.postComment(postId, content, _state.value.replyParentId) }
                .onSuccess {
                    val updated = _state.value.post?.copy(
                        commentCount = (_state.value.post?.commentCount ?: 0) + 1,
                    )
                    _state.value = _state.value.copy(
                        commentInput = "",
                        replyParentId = null,
                        replyHint = null,
                        isCommentSending = false,
                        post = updated,
                        repliesRefreshTrigger = _state.value.repliesRefreshTrigger + 1,
                        commentsRefreshTrigger = _state.value.commentsRefreshTrigger + 1,
                    )
                    updated?.let { savePost(it) }
                    onToast("评论成功")
                }
                .onFailure {
                    _state.value = _state.value.copy(isCommentSending = false)
                    onToast(it.message ?: "评论失败")
                }
        }
    }

    private fun toggleReplies(commentId: Long) {
        val expanded = _state.value.expandedCommentIds.toMutableSet()
        if (commentId in expanded) expanded.remove(commentId) else expanded.add(commentId)
        _state.value = _state.value.copy(expandedCommentIds = expanded)
    }

    private fun toggleLike() {
        val post = _state.value.post ?: return
        scope.launch {
            runCatching { api.togglePostLike(post.id) }.onSuccess {
                val newLiked = !post.isLiked
                val updated = post.copy(
                    isLiked = newLiked,
                    likeCount = if (newLiked) {
                        post.likeCount + 1
                    } else {
                        (post.likeCount - 1).coerceAtLeast(0)
                    },
                )
                _state.value = _state.value.copy(
                    post = updated,
                    likesRefreshTrigger = _state.value.likesRefreshTrigger + 1,
                )
                savePost(updated)
            }.onFailure { onToast(it.message ?: "操作失败") }
        }
    }

    private fun deleteComment(commentId: Long) {
        scope.launch {
            runCatching { api.deletePostComment(commentId) }
                .onSuccess {
                    onToast("评论已删除")
                    onCommentDeleted()
                    refresh()
                }
                .onFailure { onToast(it.message ?: "删除失败") }
        }
    }
}

/**
 * 帖子变更通知：编辑保存成功后通知仍在导航栈里的详情页/广场刷新。
 * postId = null 表示「任意帖子变更」（广场监听后整体 reload）。
 */
object FoxPostChangeNotifier {
    private val postListeners = HashMap<Long, MutableSet<() -> Unit>>()
    private val anyListeners = mutableSetOf<() -> Unit>()

    fun notifyPostChanged(postId: Long) {
        postListeners[postId]?.toList()?.forEach { it() }
        anyListeners.toList().forEach { it() }
    }

    fun watchPost(postId: Long, listener: () -> Unit): () -> Unit {
        postListeners.getOrPut(postId) { mutableSetOf() }.add(listener)
        return { postListeners[postId]?.remove(listener) }
    }

    fun watchAny(listener: () -> Unit): () -> Unit {
        anyListeners.add(listener)
        return { anyListeners.remove(listener) }
    }
}

/**
 * iOS 社区版块入口：广场 + 帖子详情。
 *
 * 两块 UI 都直接复用 feature:social commonMain 的 Content，与 Android 同一份实现；
 * 播放走 Swift 的 PlayerModel（通过 FoxIosBridge.playableSong 递数据）。
 */
object FoxCommunityScreen {

    private val api = FoxMusicApi()
    private val scope = foxMainScope()

    fun makeCommunityViewController(
        onPostClick: (Long) -> Unit,
        onSearchClick: () -> Unit,
        onCreatePost: () -> Unit,
        onPublishQueue: () -> Unit,
        onPlayMusic: (PlayableSong?, PlaybackPlaylistSource?) -> Unit,
        onReportPost: (Long, String) -> Unit,
        onSharePost: (Long, String) -> Unit,
        onEditPost: (Long) -> Unit,
        /** 帖子话题标签点击 → Swift 推搜索页（帖子 Tab + 标签，对齐 Android onTagClick）。 */
        onTagClick: (String) -> Unit = {},
        onToast: (String) -> Unit,
        /** 左上角三横「更多」→ Swift 打开左侧抽屉。 */
        onOpenDrawer: () -> Unit = {},
    ): UIViewController = foxComposeViewController {
        val presenter = remember { CommunityPlazaPresenter(api, scope, onToast) }
        val state by presenter.state.collectAsState()

        // 任意帖子编辑保存后，广场整体刷新。
        DisposableEffect(Unit) {
            val dispose = FoxPostChangeNotifier.watchAny { presenter.reload() }
            onDispose { dispose() }
        }

        CommunityPlazaContent(
            state = state,
            onIntent = presenter::handle,
            modifier = Modifier,
            onSearchClick = onSearchClick,
            onCreatePost = onCreatePost,
            onPublishQueue = onPublishQueue,
            onPostDetail = onPostClick,
            onPlayMusic = { post ->
                post.music?.let { music ->
                    onPlayMusic(
                        FoxIosBridge.playableSong(music),
                        PlaybackPlaylistSource.fromPost(post),
                    )
                }
            },
            onNavigateToPlayer = { },
            onReportPost = { post -> onReportPost(post.id, post.title ?: "未命名帖子") },
            onSharePost = { post ->
                onSharePost(post.id, post.title?.takeIf { it.isNotBlank() } ?: post.content.take(50))
            },
            onEditPost = onEditPost,
            onDeletePost = presenter::deletePost,
            onTagClick = onTagClick,
            onOpenDrawer = onOpenDrawer,
        )
    }

    fun makePostDetailViewController(
        postId: Long,
        currentUserId: Long,
        onBack: () -> Unit,
        onPlayMusic: (PlayableSong?, PlaybackPlaylistSource?) -> Unit,
        onToast: (String) -> Unit,
        onCommentDeleted: () -> Unit,
        onReportPost: (Long, String) -> Unit,
        /** 作者头像 / 评论者 / 点赞者点击 → Swift 推个人主页（对齐 Android onNavigateToUserHome）。 */
        onNavigateToUserHome: (Long) -> Unit,
        /** 编辑自己的帖子 → Swift 推编辑页。 */
        onEditPost: (Long) -> Unit,
        /** 分享帖子 (postId, 展示文案) → Swift 弹「分享给好友 / 系统分享」。 */
        onSharePost: (Long, String) -> Unit,
        /** 评论举报 → Swift 弹统一举报弹窗。 */
        onReportComment: (Long) -> Unit,
        /** 帖子删除成功 → Swift 关闭详情页。 */
        onPostDeleted: () -> Unit,
        /** 帖子话题标签点击 → Swift 推搜索页（帖子 Tab + 标签）。 */
        onTagClick: (String) -> Unit = {},
        /**
         * 迷你播放条高度（pt）。iOS 的迷你条是 SwiftUI 常驻浮层，画在页面之外，
         * 不预留就会盖住评论输入栏 —— 由 Swift 侧传入实测高度把底栏顶上去。
         * 0 表示当前没有迷你条。
         */
        bottomBarBottomInsetDp: Double,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val presenter = remember(postId) {
            PostDetailPresenter(
                postId = postId,
                api = api,
                scope = scope,
                currentUserId = currentUserId,
                onToast = onToast,
                onCommentDeleted = onCommentDeleted,
                onReportComment = onReportComment,
                onPostDeleted = onPostDeleted,
            )
        }
        val state by presenter.state.collectAsState()
        val comments = presenter.commentsFlow.collectAsLazyPagingItems()
        val likes = presenter.likesFlow.collectAsLazyPagingItems()

        // 本帖编辑保存返回后自动刷新详情。
        DisposableEffect(postId) {
            val dispose = FoxPostChangeNotifier.watchPost(postId) { presenter.refresh() }
            onDispose { dispose() }
        }

        PostDetailContent(
            state = state,
            comments = comments,
            likes = likes,
            onIntent = presenter::handle,
            modifier = Modifier,
            currentUserId = currentUserId.takeIf { it > 0 },
            isLoggedIn = currentUserId > 0,
            repliesFlow = presenter::repliesFlow,
            bottomBarBottomInset = bottomBarBottomInsetDp.dp,
            onBack = onBack,
            onPlayMusic = { post ->
                post.music?.let { music ->
                    onPlayMusic(
                        FoxIosBridge.playableSong(music),
                        PlaybackPlaylistSource.fromPost(post),
                    )
                }
            },
            onCommentDeleted = onCommentDeleted,
            onReportPost = { post -> onReportPost(post.id, post.title ?: "未命名帖子") },
            onNavigateToUserHome = onNavigateToUserHome,
            onSharePost = { post ->
                onSharePost(post.id, post.title?.takeIf { it.isNotBlank() } ?: post.content.take(50))
            },
            onEditPost = onEditPost,
            onDeletePost = { presenter.deleteCurrentPost() },
            onTagClick = onTagClick,
        )
    }
}
