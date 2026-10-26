package com.fox.music.iosshared.ui

import kotlinx.coroutines.MainScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fox.music.iosshared.foxComposeViewController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.feature.search.SearchContent
import com.fox.music.feature.search.SearchIntent
import com.fox.music.feature.search.SearchMusicPlatform
import com.fox.music.feature.search.SearchResultTab
import com.fox.music.feature.search.SearchState
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * 搜索页分页源（单曲）：平台曲库走 /api/music?keyword=，
 * 第三方平台（QQ/网易云）走 /api/music/third-party/search（均 noAuth）。
 */
internal class SearchMusicPagingSource(
    private val api: FoxMusicApi,
    private val keyword: String,
    private val platform: SearchMusicPlatform,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val page = params.key ?: 1
        if (keyword.isBlank()) {
            LoadResult.Page(emptyList(), null, null)
        } else {
            val dtos = if (platform == SearchMusicPlatform.FOX) {
                api.searchMusic(keyword, page, params.loadSize)
            } else {
                api.searchThirdPartyMusic(platform.apiValue ?: "qq", keyword, page, params.loadSize)
            }
            val list = dtos.map { it.toMusic() }
            LoadResult.Page(
                data = list,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (list.size < params.loadSize) null else page + 1,
            )
        }
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? = null
}

/** 搜索页分页源（歌手）：GET /api/artists?keyword=。 */
internal class SearchArtistPagingSource(
    private val api: FoxMusicApi,
    private val keyword: String,
) : PagingSource<Int, Artist>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artist> = try {
        val page = params.key ?: 1
        val list = if (keyword.isBlank()) emptyList()
        else api.searchArtists(keyword, page, params.loadSize).map { it.toArtist() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Artist>): Int? = null
}

/** 搜索页分页源（专辑）：GET /api/albums?keyword=。 */
internal class SearchAlbumPagingSource(
    private val api: FoxMusicApi,
    private val keyword: String,
) : PagingSource<Int, Album>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Album> = try {
        val page = params.key ?: 1
        val list = if (keyword.isBlank()) emptyList()
        else api.searchAlbums(keyword, page, params.loadSize).map { it.toAlbum() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Album>): Int? = null
}

/**
 * iOS 端搜索页 Presenter：复用 feature:search commonMain 的 SearchContent ——
 * 与 Android 搜索页同一份 UI（搜索框+单曲/歌手/专辑/帖子 Tab+平台切换+热词面板）。
 *
 * 查询状态 Kotlin 侧生产（Ktor + Paging3）；Swift 只接返回/播放/帖子导航回调。
 * 交互约定：Search / 点热词 → 同时落 committed 查询并收起焦点（showRecommendPanel=false）→ 展示结果。
 *
 * 帖子 Tab：与 Android SearchScreen 的 postResultsContent 注入同一套路 ——
 * PostNoteGridCard 在 core:ui commonMain 有 iOS 实现（PostVideoCover iosMain），可直接用。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxSearchScreen {

    private val api = FoxMusicApi()
    private val scope = foxMainScope()

    fun makeSearchViewController(
        onBack: () -> Unit,
        onMusicClick: (PlayableSong?) -> Unit,
        onArtistClick: (Long) -> Unit,
        onAlbumClick: (Long) -> Unit,
        onPostClick: (Long) -> Unit,
        onToast: (String) -> Unit,
        onMusicMoreClick: (Music) -> Unit = {},
        /**
         * 从帖子标签深链进入：非空时直接落到「帖子」Tab，
         * 搜索框回填该标签、postTag 同步带上并自动出结果
         * （与 Android searchRoute(tab=POST, tag=...) 同口径）。
         */
        initialTag: String? = null,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val deepTag = initialTag?.takeIf { it.isNotBlank() }
        var query by remember { mutableStateOf(deepTag.orEmpty()) }
        var committed by remember { mutableStateOf(deepTag.orEmpty()) }
        var selectedTab by remember {
            mutableStateOf(if (deepTag != null) SearchResultTab.POST else SearchResultTab.MUSIC)
        }
        var platform by remember { mutableStateOf(SearchMusicPlatform.FOX) }
        var hasSearched by remember { mutableStateOf(deepTag != null) }
        var focused by remember { mutableStateOf(false) }
        var historyExpanded by remember { mutableStateOf(false) }
        var hotKeywords by remember { mutableStateOf(emptyList<HotKeyword>()) }
        var postTag by remember { mutableStateOf<String?>(deepTag) }
        var postLikeOverrides by remember { mutableStateOf(emptyMap<Long, Pair<Boolean, Int>>()) }
        // 下拉/重试语义的刷新计数：并进 remember 键，committed 未变时也能真正重建分页
        var refreshTick by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            hotKeywords = try {
                api.fetchHotKeywords(8)
            } catch (_: Exception) {
                emptyList()
            }
        }

        // 结果分页：随 committed / platform 重建（未搜索时 query 为空 → 空页）
        val musicFlow = remember(committed, platform, refreshTick) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                SearchMusicPagingSource(api, committed, platform)
            }.flow.cachedIn(scope)
        }
        val artistFlow = remember(committed, refreshTick) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                SearchArtistPagingSource(api, committed)
            }.flow.cachedIn(scope)
        }
        val albumFlow = remember(committed, refreshTick) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                SearchAlbumPagingSource(api, committed)
            }.flow.cachedIn(scope)
        }
        // 帖子结果（与 Android SearchViewModel.performSearch 同口径）：
        // 标签模式下 keyword 必须为 null（query 里的标签仅用于搜索框回显），
        // 否则后端 tag AND keyword 叠加过滤会返回空；标签流默认 sort=hot。
        val postFlow = remember(committed, postTag, refreshTick) {
            if (committed.isBlank() && postTag.isNullOrBlank()) {
                Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                    SearchPostEmptyPagingSource()
                }.flow.cachedIn(scope)
            } else {
                Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                    PostPagingSource(
                        api,
                        sort = if (!postTag.isNullOrBlank()) "hot" else "latest",
                        tag = postTag,
                        keyword = committed.takeIf { it.isNotBlank() && postTag.isNullOrBlank() },
                    )
                }.flow.cachedIn(scope)
            }
        }
        val postPagingItems = postFlow.collectAsLazyPagingItems()

        val state = SearchState(
            query = query,
            selectedTab = selectedTab,
            selectedMusicPlatform = platform,
            musicResults = musicFlow,
            artistResults = artistFlow,
            albumResults = albumFlow,
            postResults = postFlow,
            postTag = postTag,
            postLikeOverrides = postLikeOverrides,
            searchHistory = emptyList(), // 需登录接口，iOS 登录批次接入
            isHistoryExpanded = historyExpanded,
            hotKeywords = hotKeywords,
            hotKeywordsHasMore = false,
            hasSearched = hasSearched,
            isSearchFocused = focused,
        )

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "search") {
                SearchContent(
                    state = state,
                    onIntent = { intent ->
                        when (intent) {
                            is SearchIntent.QueryChange -> query = intent.query
                            is SearchIntent.Search -> {
                                committed = query.trim()
                                hasSearched = committed.isNotEmpty()
                                focused = false
                            }
                            is SearchIntent.SelectHotKeyword -> {
                                query = intent.keyword
                                committed = intent.keyword
                                hasSearched = true
                                focused = false
                            }
                            is SearchIntent.SelectHistory -> {
                                query = intent.keyword
                                committed = intent.keyword
                                hasSearched = true
                                focused = false
                            }
                            is SearchIntent.SelectSuggest -> {
                                query = intent.suggest.text
                                committed = intent.suggest.text
                                hasSearched = true
                                focused = false
                            }
                            is SearchIntent.FocusChanged -> focused = intent.focused
                            is SearchIntent.SelectTab -> selectedTab = intent.tab
                            is SearchIntent.SelectMusicPlatform -> platform = intent.platform
                            is SearchIntent.ToggleHistoryExpanded ->
                                historyExpanded = !historyExpanded
                            is SearchIntent.ClearHistory -> Unit // 历史需登录，暂不接
                            is SearchIntent.DeleteHistory -> Unit // 历史需登录，暂不接
                            is SearchIntent.RefreshHotKeywords -> Unit
                            is SearchIntent.RefreshSearch -> refreshTick++
                            is SearchIntent.ClearPostTag -> postTag = null
                            is SearchIntent.FilterPostTag -> postTag = intent.tag
                            is SearchIntent.TogglePostLike -> togglePostLike(
                                intent.post,
                                postLikeOverrides,
                                onLikeChanged = { liked, count ->
                                    postLikeOverrides = postLikeOverrides +
                                        (intent.post.id to (liked to count))
                                },
                                onToast = onToast,
                            )
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onBack = onBack,
                    onMusicClick = { onMusicClick(FoxIosBridge.playableSong(it)) },
                    onArtistClick = onArtistClick,
                    onAlbumClick = { album -> onAlbumClick(album.id) },
                    onMusicMoreClick = onMusicMoreClick,
                    applyPostOverrides = { post ->
                        postLikeOverrides[post.id]?.let { (liked, count) ->
                            post.copy(isLiked = liked, likeCount = count)
                        } ?: post
                    },
                    postResultsContent = {
                        PostSearchResultsContent(
                            pagingItems = postPagingItems,
                            onPostClick = onPostClick,
                            onLikeClick = { post ->
                                togglePostLike(
                                    post,
                                    postLikeOverrides,
                                    onLikeChanged = { liked, count ->
                                        postLikeOverrides = postLikeOverrides +
                                            (post.id to (liked to count))
                                    },
                                    onToast = onToast,
                                )
                            },
                            onTagClick = { postTag = it },
                        )
                    },
                )
            }
        }
    }

    private fun togglePostLike(
        post: com.fox.music.core.model.social.Post,
        overrides: Map<Long, Pair<Boolean, Int>>,
        onLikeChanged: (Boolean, Int) -> Unit,
        onToast: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { api.togglePostLike(post.id) }.onSuccess {
                val newLiked = !post.isLiked
                val newCount = if (newLiked) {
                    post.likeCount + 1
                } else {
                    (post.likeCount - 1).coerceAtLeast(0)
                }
                onLikeChanged(newLiked, newCount)
            }.onFailure { onToast(it.message ?: "操作失败") }
        }
    }
}

/** 未搜索时帖子结果占位：空页（与 Android flowOf(PagingData.empty()) 同语义）。 */
internal class SearchPostEmptyPagingSource : PagingSource<Int, com.fox.music.core.model.social.Post>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, com.fox.music.core.model.social.Post> =
        LoadResult.Page(emptyList(), null, null)

    override fun getRefreshKey(state: PagingState<Int, com.fox.music.core.model.social.Post>): Int? = null
}

/**
 * 帖子搜索结果（瀑布流 2 列）：与 Android SearchScreen.PostSearchResults 同一份布局，
 * 卡片复用 core:ui commonMain 的 PostNoteGridCard（iOS 侧视频封面有 iosMain 实现）。
 */
@Composable
private fun PostSearchResultsContent(
    pagingItems: androidx.paging.compose.LazyPagingItems<com.fox.music.core.model.social.Post>,
    onPostClick: (Long) -> Unit,
    onLikeClick: (com.fox.music.core.model.social.Post) -> Unit,
    onTagClick: (String) -> Unit,
) {
    when {
        pagingItems.loadState.refresh is androidx.paging.LoadState.Loading -> {
            com.fox.music.core.ui.component.LoadingIndicator(useLottie = false)
        }

        pagingItems.loadState.refresh is androidx.paging.LoadState.Error -> {
            com.fox.music.core.ui.component.ErrorView(
                modifier = androidx.compose.ui.Modifier,
                message = (pagingItems.loadState.refresh as androidx.paging.LoadState.Error)
                    .error.message ?: "搜索失败",
                onRetry = { pagingItems.retry() },
            )
        }

        pagingItems.loadState.refresh is androidx.paging.LoadState.NotLoading &&
            pagingItems.itemCount == 0 -> {
            com.fox.music.core.ui.component.ErrorView(
                modifier = androidx.compose.ui.Modifier,
                message = "未找到相关帖子",
                showIcon = false,
            )
        }

        else -> {
            androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2),
                modifier = androidx.compose.ui.Modifier,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 8.dp,
                ),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.id },
                ) { index ->
                    val post = pagingItems[index] ?: return@items
                    com.fox.music.core.ui.component.PostNoteGridCard(
                        post = post,
                        onClick = { onPostClick(post.id) },
                        onLikeClick = { onLikeClick(post) },
                        onTagClick = onTagClick,
                    )
                }
                if (pagingItems.loadState.append is androidx.paging.LoadState.Loading) {
                    item(
                        key = "loading_more",
                        span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine,
                    ) {
                        com.fox.music.core.ui.component.LoadingIndicator(useLottie = false)
                    }
                }
            }
        }
    }
}
