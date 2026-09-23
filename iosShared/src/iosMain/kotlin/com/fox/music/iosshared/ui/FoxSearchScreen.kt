package com.fox.music.iosshared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
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
import kotlinx.coroutines.MainScope
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
 * 查询状态 Kotlin 侧生产（Ktor + Paging3）；Swift 只接返回/播放回调。
 * 交互约定：Search / 点热词 → 同时落 committed 查询并收起焦点（showRecommendPanel=false）→ 展示结果。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxSearchScreen {

    private val api = FoxMusicApi()
    private val scope = MainScope()

    fun makeSearchViewController(
        onBack: () -> Unit,
        onMusicClick: (PlayableSong?) -> Unit,
        onArtistClick: (Long) -> Unit,
        onAlbumClick: (Long) -> Unit,
    ): UIViewController = ComposeUIViewController {
        var query by remember { mutableStateOf("") }
        var committed by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(SearchResultTab.MUSIC) }
        var platform by remember { mutableStateOf(SearchMusicPlatform.FOX) }
        var hasSearched by remember { mutableStateOf(false) }
        var focused by remember { mutableStateOf(false) }
        var historyExpanded by remember { mutableStateOf(false) }
        var hotKeywords by remember { mutableStateOf(emptyList<HotKeyword>()) }

        LaunchedEffect(Unit) {
            hotKeywords = try {
                api.fetchHotKeywords(8)
            } catch (_: Exception) {
                emptyList()
            }
        }

        // 结果分页：随 committed / platform 重建（未搜索时 query 为空 → 空页）
        val musicFlow = remember(committed, platform) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                SearchMusicPagingSource(api, committed, platform)
            }.flow.cachedIn(scope)
        }
        val artistFlow = remember(committed) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                SearchArtistPagingSource(api, committed)
            }.flow.cachedIn(scope)
        }
        val albumFlow = remember(committed) {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                SearchAlbumPagingSource(api, committed)
            }.flow.cachedIn(scope)
        }

        val state = SearchState(
            query = query,
            selectedTab = selectedTab,
            selectedMusicPlatform = platform,
            musicResults = musicFlow,
            artistResults = artistFlow,
            albumResults = albumFlow,
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
                            is SearchIntent.RefreshHotKeywords -> Unit
                            is SearchIntent.ClearPostTag -> Unit
                            is SearchIntent.FilterPostTag -> Unit
                            is SearchIntent.TogglePostLike -> Unit
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onBack = onBack,
                    onMusicClick = { onMusicClick(FoxIosBridge.playableSong(it)) },
                    onArtistClick = onArtistClick,
                    onAlbumClick = { album -> onAlbumClick(album.id) },
                )
            }
        }
    }
}
