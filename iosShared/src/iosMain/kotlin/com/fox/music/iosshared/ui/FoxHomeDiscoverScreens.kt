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
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toPlaylistDetail
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail
import com.fox.music.feature.discover.DiscoverContent
import com.fox.music.feature.discover.DiscoverIntent
import com.fox.music.feature.discover.DiscoverState
import com.fox.music.feature.home.HomeContent
import com.fox.music.feature.home.HomeIntent
import com.fox.music.feature.home.HomeState
import com.fox.music.feature.playlist.ui.component.PlaylistDetailContent
import com.fox.music.feature.playlist.viewmodel.HeaderInfo
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailState
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.PlayableSong
import kotlinx.coroutines.MainScope
import platform.UIKit.UIViewController

/**
 * 歌曲列表分页源（首页"为你精选"）：与 Android 端 GetMusicListUseCase 同一后端接口
 * （GET /api/music，noAuth）。
 */
internal class MusicListPagingSource(
    private val api: FoxMusicApi,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val page = params.key ?: 1
        val list = api.fetchMusicList(page, params.loadSize).map { it.toMusic() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

/**
 * iOS 端首页 Presenter：复用 feature:home commonMain 的 HomeContent ——
 * 与 Android 首页完全同一份 UI（品牌头+搜索+最近在听+推荐歌单+为你精选+热门专辑）。
 *
 * 数据全部 Kotlin 侧生产（Ktor + Paging3），Swift 只接导航/播放回调：
 * - onMusicClick：Kotlin 侧解析好 PlayableSong 再回调（Swift 直接喂 AVPlayer）
 * - onPlaylistClick：(id, title, isRank) 三个原始值，Swift 据此 push 歌单详情页
 * - 最近在听：后端 music-history 系列接口强制登录，iOS 未登录期间置空（HomeContent 自带空态）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxHomeScreen {

    private val scope = MainScope()
    private val api = FoxMusicApi()

    private val recommendedMusic = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
        MusicListPagingSource(api)
    }.flow.cachedIn(scope)

    fun makeHomeViewController(
        onMusicClick: (PlayableSong?) -> Unit,
        onPlaylistClick: (Long, String, Boolean) -> Unit,
        onSearchClick: () -> Unit,
    ): UIViewController = ComposeUIViewController {
        var hotKeywords by remember { mutableStateOf(emptyList<HotKeyword>()) }
        var playlists by remember { mutableStateOf(emptyList<Playlist>()) }
        var albums by remember { mutableStateOf(emptyList<Album>()) }

        LaunchedEffect(Unit) {
            try {
                val kw = try {
                    api.fetchHotKeywords(8)
                } catch (_: Exception) {
                    emptyList()
                }
                val pl = api.fetchRecommendedPlaylists(1, 20).map { it.toPlaylist() }
                val al = try {
                    api.fetchAlbums(1, 8).map { it.toAlbum() }
                } catch (_: Exception) {
                    emptyList()
                }
                hotKeywords = kw
                playlists = pl
                albums = al
            } catch (e: Exception) {
                println("FoxHomeScreen: 首页数据加载失败: ${e.message}")
            }
        }

        val state = HomeState(
            hotKeywords = hotKeywords,
            recentPlays = emptyList(), // 需登录接口，iOS 登录批次接入
            recommendedPlaylists = playlists,
            recommendedMusic = recommendedMusic,
            recommendedAlbums = albums,
        )

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "home") {
                HomeContent(
                    state = state,
                    onIntent = { intent ->
                        when (intent) {
                            is HomeIntent.OnPlaylistClick ->
                                onPlaylistClick(intent.playlist.id, intent.playlist.title, false)
                            is HomeIntent.OnAlbumClick -> Unit // 专辑详情后续批次接入
                            else -> Unit
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onMusicClick = { music -> onMusicClick(FoxIosBridge.playableSong(music)) },
                    onSearchClick = onSearchClick,
                )
            }
        }
    }
}

/**
 * iOS 端发现页 Presenter：复用 feature:discover commonMain 的 DiscoverContent ——
 * 与 Android 发现页完全同一份 UI（热搜词轮播+热门榜单横滑卡片+新歌速递+热门歌手）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxDiscoverScreen {

    private val api = FoxMusicApi()

    private var state by mutableStateOf(DiscoverUiState())

    private data class DiscoverUiState(
        val hotKeywords: List<HotKeyword> = emptyList(),
        val ranks: List<Playlist> = emptyList(),
        val rankDetails: Map<Long, PlaylistDetail> = emptyMap(),
        val newMusic: List<Music> = emptyList(),
        val hotArtists: List<Artist> = emptyList(),
        val error: String? = null,
    )

    fun makeDiscoverViewController(
        onMusicClick: (PlayableSong?) -> Unit,
        onRankClick: (Long, String) -> Unit,
        onArtistClick: (Long) -> Unit,
        onSearchClick: () -> Unit,
    ): UIViewController = ComposeUIViewController {

        LaunchedEffect(Unit) {
            if (state.ranks.isNotEmpty()) return@LaunchedEffect
            try {
                val kw = try {
                    api.fetchHotKeywords(8)
                } catch (_: Exception) {
                    emptyList()
                }
                val ranks = api.fetchRanks(1, 10).map { it.toPlaylist() }
                // 每个榜单只取前 4 首用于卡片预览（与 Android 端 RankCard 展示一致）
                val details = ranks.take(5).mapNotNull { rank ->
                    api.fetchRankDetail(rank.id, page = 1, limit = 4)?.let { dto ->
                        rank.id to dto.toPlaylistDetail()
                    }
                }.toMap()
                val newMusic = api.fetchMusicList(1, 5).map { it.toMusic() }
                val artists = try {
                    api.fetchArtists(1, 8).map { it.toArtist() }
                } catch (_: Exception) {
                    emptyList()
                }
                state = DiscoverUiState(
                    hotKeywords = kw,
                    ranks = ranks,
                    rankDetails = details,
                    newMusic = newMusic,
                    hotArtists = artists,
                )
            } catch (e: Exception) {
                println("FoxDiscoverScreen: 发现页数据加载失败: ${e.message}")
                state = state.copy(error = e.message)
            }
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "discover") {
                DiscoverContent(
                    state = DiscoverState(
                        hotKeywords = state.hotKeywords,
                        ranks = state.ranks,
                        rankDetails = state.rankDetails,
                        newMusic = state.newMusic,
                        hotArtists = state.hotArtists,
                        error = state.error,
                    ),
                    onIntent = { intent ->
                        when (intent) {
                            is DiscoverIntent.OnRankClick ->
                                onRankClick(intent.rank.id, intent.rank.title)
                            is DiscoverIntent.OnMusicClick ->
                                onMusicClick(FoxIosBridge.playableSong(intent.music))
                            is DiscoverIntent.OnArtistClick ->
                                onArtistClick(intent.artist.id)
                            else -> Unit
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onSearchClick = onSearchClick,
                )
            }
        }
    }
}

/**
 * 歌单/榜单详情页 Presenter：复用 feature:playlist commonMain 的 PlaylistDetailContent ——
 * 与 Android 歌单详情页完全同一份 UI（大封面头部+播放全部/收藏+分页曲目）。
 *
 * useRankEndpoint=true 时走 /api/playlists/ranks/{id}（发现页榜单点入），
 * 否则走 /api/playlists/{id}（歌单广场/首页点入）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxPlaylistDetailScreen {

    private val api = FoxMusicApi()

    fun makePlaylistDetailViewController(
        playlistId: Long,
        useRankEndpoint: Boolean,
        onMusicClick: (PlayableSong?) -> Unit,
        onPlayAll: (List<PlayableSong>) -> Unit,
        onBack: () -> Unit,
    ): UIViewController = ComposeUIViewController {
        var header by remember { mutableStateOf<HeaderInfo?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        val tracksFlow = remember(playlistId, useRankEndpoint) {
            Pager(PagingConfig(pageSize = 30, initialLoadSize = 30)) {
                PlaylistTracksPagingSource(api, playlistId, useRankEndpoint)
            }.flow.cachedIn(MainScope())
        }
        val pagingItems = tracksFlow.collectAsLazyPagingItems()

        LaunchedEffect(playlistId, useRankEndpoint) {
            try {
                val detail = if (useRankEndpoint) {
                    api.fetchRankDetail(playlistId)
                } else {
                    api.fetchPlaylistDetail(playlistId)
                }
                header = detail?.let {
                    HeaderInfo.fromPlaylist(
                        it.toPlaylistDetail().playlist,
                        DetailType.PLAYLIST,
                    )
                }
            } catch (e: Exception) {
                error = e.message
            }
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "playlist-detail") {
                PlaylistDetailContent(
                    state = PlaylistDetailState(
                        headerInfo = header,
                        isLoading = header == null && error == null,
                        error = error,
                    ),
                    pagingItems = pagingItems,
                    playlistKey = "playlist_$playlistId",
                    playlistId = playlistId,
                    canManagePlaylist = false, // 详情页管理能力（编辑/删除）随登录批次接入
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onMusicClick = { music -> onMusicClick(FoxIosBridge.playableSong(music)) },
                    onPlayAll = {
                        val firstPage = pagingItems.itemSnapshotList.items
                        onPlayAll(firstPage.mapNotNull { FoxIosBridge.playableSong(it) })
                    },
                    onToggleFavorite = {}, // 需登录，后续批次接入
                    onMusicMoreClick = {},
                    onArtistClick = {},
                    onEnterSelectionMode = {},
                    onToggleSelection = {},
                    onBack = onBack,
                    onExitSelectionMode = {},
                    onSelectAll = {},
                    onRefresh = {},
                    onAddSelectedToQueue = {},
                    onRemoveSelectedFromPlaylist = {},
                    onAddSelectedToPlaylist = {},
                    onDownloadSelected = {},
                )
            }
        }
    }
}

/** 歌单/榜单曲目分页源：详情接口的 tracks 字段本身就是分页结构。 */
internal class PlaylistTracksPagingSource(
    private val api: FoxMusicApi,
    private val playlistId: Long,
    private val useRankEndpoint: Boolean,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val page = params.key ?: 1
        val detail = if (useRankEndpoint) {
            api.fetchRankDetail(playlistId, page, params.loadSize)
        } else {
            api.fetchPlaylistDetail(playlistId, page, params.loadSize)
        }
        val list = detail?.tracks?.list.orEmpty().map { it.toMusic() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
