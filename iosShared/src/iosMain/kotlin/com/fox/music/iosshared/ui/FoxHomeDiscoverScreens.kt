package com.fox.music.iosshared.ui

import kotlinx.coroutines.MainScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toArtistDetail
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPlayHistory
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toPlaylistDetail
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PlayHistoryDto
import com.fox.music.core.network.model.PlaylistDto
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail
import com.fox.music.feature.discover.DiscoverContent
import com.fox.music.feature.discover.DiscoverIntent
import com.fox.music.feature.discover.DiscoverState
import com.fox.music.feature.discover.ArtistDetailContent
import com.fox.music.feature.discover.ArtistDetailIntent
import com.fox.music.feature.discover.ArtistDetailState
import com.fox.music.feature.home.HomeContent
import com.fox.music.feature.home.HomeIntent
import com.fox.music.feature.home.HomeState
import com.fox.music.feature.playlist.ui.component.PlaylistDetailContent
import com.fox.music.feature.playlist.viewmodel.HeaderInfo
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailState
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxFirstPageCache
import com.fox.music.iosshared.FoxMusicTopListCache
import com.fox.music.iosshared.MusicTopListPagingSource
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.HomeFirstPageDto
import com.fox.music.iosshared.DiscoverFirstPageDto
import com.fox.music.iosshared.RankDetailEntry
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIViewController

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

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    // 与 Android HomeViewModel 对齐：getPagingSource(limit = 5, sort = "recommend")。
    // 数据源同详情页：一整份当日 100 条快照（FoxMusicTopListCache），这里只切前 5 条。
    private val recommendedMusic = Pager(PagingConfig(pageSize = 5, initialLoadSize = 5)) {
        MusicTopListPagingSource(api, sort = FoxMusicTopListCache.SORT_RECOMMEND)
    }.flow.cachedIn(scope)

    /**
     * 播放队列预取池：首页点击歌曲时，队列不该只有屏幕上已加载的几条
     * （Android 端 Paging 会随滚动预取，入队列表天然更大）。这里在进入首页时
     * 后台多拉 30 条「recommend」，点歌时优先用大池子入队 —— 多加载但不展示完。
     */
    private var recommendQueuePool: List<Music> = emptyList()

    fun makeHomeViewController(
        onMusicClickFromList: (List<PlayableSong>, Int) -> Unit,
        onPlaylistClick: (Long, String, Boolean) -> Unit,
        onAlbumClick: (Long) -> Unit,
        onSearchClick: () -> Unit,
        onPlaylistCategoryClick: () -> Unit,
        onAlbumCategoryClick: () -> Unit,
        onRecommendClick: () -> Unit,
        onMusicMoreClick: (Music) -> Unit = {},
        // AI 音乐助手入口（功能二）：传 null 不渲染 pill（与 HomeContent 约定一致）
        onAssistantClick: (() -> Unit)? = null,
        /** 左上角三横「更多」→ Swift 打开左侧抽屉。 */
        onOpenDrawer: () -> Unit = {},
    ): UIViewController = foxComposeViewController {
        var hotKeywords by remember { mutableStateOf(emptyList<HotKeyword>()) }
        var playlists by remember { mutableStateOf(emptyList<Playlist>()) }
        var albums by remember { mutableStateOf(emptyList<Album>()) }
        var recentPlays by remember { mutableStateOf(emptyList<PlayHistory>()) }
        // "为你精选"当前已加载的完整列表：点某首歌时要拿它当播放队列，
        // 否则 iOS 队列里只有一首，上一首/下一首失效（Android 端是 updatePlaylist 整列表入队）
        var recommendedSnapshot by remember { mutableStateOf(emptyList<Music>()) }
        val authRevision = FoxAuthStore.revision

        LaunchedEffect(authRevision) {
            val owner = FoxFirstPageCache.ownerPrefix()
            val cacheKey = "${owner}__home_v1"
            // 1) 冷启动：命中磁盘缓存先秒出，避免满屏骨架/占位
            FoxFirstPageCache.load(cacheKey, HomeFirstPageDto.serializer())?.let { c ->
                hotKeywords = c.hotKeywords
                playlists = c.playlists.map { it.toPlaylist() }
                albums = c.albums.map { it.toAlbum() }
                recentPlays = c.recentPlays.map { it.toPlayHistory() }
                recommendQueuePool = c.recommendPool.map { it.toMusic() }
            }
            // 2) 队列预取池与首页数据并行刷新（pool 保留 DTO 以便存盘）
            val poolDeferred = scope.async {
                try {
                    api.fetchMusicList(1, 30, sort = "recommend")
                } catch (_: Exception) {
                    emptyList<MusicDto>()
                }
            }
            try {
                // 一级请求全部并发（旧实现串行 await，慢接口会把整页拖到十几秒）
                val kwDeferred = scope.async {
                    try { api.fetchHotKeywords(8) } catch (_: Exception) { emptyList<HotKeyword>() }
                }
                val plDeferred = scope.async {
                    try { api.fetchRecommendedPlaylists(1, 8) } catch (_: Exception) { emptyList<PlaylistDto>() }
                }
                val alDeferred = scope.async {
                    try { api.fetchAlbums(1, 8) } catch (_: Exception) { emptyList<AlbumDto>() }
                }
                val historyDeferred = scope.async {
                    if (FoxAuthStore.isLoggedIn) {
                        try { api.fetchPlayHistory(1, 20) } catch (_: Exception) { emptyList<PlayHistoryDto>() }
                    } else {
                        emptyList<PlayHistoryDto>()
                    }
                }
                val kw = kwDeferred.await()
                val plDto = plDeferred.await()
                val alDto = alDeferred.await()
                val historyDto = historyDeferred.await()
                hotKeywords = kw
                playlists = plDto.map { it.toPlaylist() }
                albums = alDto.map { it.toAlbum() }
                recentPlays = historyDto.map { it.toPlayHistory() }
                val poolDto = poolDeferred.await()
                recommendQueuePool = poolDto.map { it.toMusic() }
                // 3) 存盘：下次冷启动直接命中（取到新数据后刷新缓存）
                FoxFirstPageCache.save(
                    cacheKey,
                    HomeFirstPageDto.serializer(),
                    HomeFirstPageDto(
                        hotKeywords = kw,
                        playlists = plDto,
                        albums = alDto,
                        recentPlays = historyDto,
                        recommendPool = poolDto,
                    ),
                )
            } catch (e: Exception) {
                println("FoxHomeScreen: 首页数据加载失败: ${e.message}")
            }
        }

        val state = HomeState(
            hotKeywords = hotKeywords,
            recentPlays = recentPlays,
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
                            is HomeIntent.OnAlbumClick -> onAlbumClick(intent.album.id)
                            else -> Unit
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onMusicClick = { music ->
                        // 队列优先用预取池（30 条），屏幕上只展示前几条 —— 与 Android 的
                        // "Paging 预取后整列表入队"体验对齐；池子没拉到就用已加载快照兜底。
                        val pool = recommendQueuePool.ifEmpty { recommendedSnapshot }
                        val (songs, index) = queueOf(pool, music)
                        onMusicClickFromList(songs, index)
                    },
                    onRecommendedMusicChanged = { recommendedSnapshot = it },
                    onSearchClick = onSearchClick,
                    onPlaylistMoreClick = onPlaylistCategoryClick,
                    onAlbumMoreClick = onAlbumCategoryClick,
                    onRecommendClick = onRecommendClick,
                    onMusicMoreClick = onMusicMoreClick,
                    onAssistantClick = onAssistantClick,
                    onOpenDrawer = onOpenDrawer,
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

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    private var state by mutableStateOf(DiscoverUiState())

    /**
     * 新歌速递播放队列预取池：屏幕只展示 5 条，点歌入队时用更大的池子
     * （30 条「latest」，多加载但不展示完，与首页 recommend 池同一套路）。
     */
    private var newMusicQueuePool: List<Music> = emptyList()

    private data class DiscoverUiState(
        val hotKeywords: List<HotKeyword> = emptyList(),
        val ranks: List<Playlist> = emptyList(),
        val rankDetails: Map<Long, PlaylistDetail> = emptyMap(),
        val newMusic: List<Music> = emptyList(),
        val hotArtists: List<Artist> = emptyList(),
        val error: String? = null,
    )

    fun makeDiscoverViewController(
        onMusicClickFromList: (List<PlayableSong>, Int) -> Unit,
        onRankClick: (Long, String) -> Unit,
        onArtistClick: (Long) -> Unit,
        onSearchClick: () -> Unit,
        onNewMusicMore: () -> Unit,
        onHotArtistMore: () -> Unit = {},
        onMusicMoreClick: (Music) -> Unit = {},
        /** 左上角三横「更多」→ Swift 打开左侧抽屉。 */
        onOpenDrawer: () -> Unit = {},
    ): UIViewController = foxComposeViewController {

        LaunchedEffect(Unit) {
            if (state.ranks.isNotEmpty()) return@LaunchedEffect
            val owner = FoxFirstPageCache.ownerPrefix()
            val cacheKey = "${owner}__discover_v1"
            // 1) 冷启动：命中磁盘缓存先秒出
            FoxFirstPageCache.load(cacheKey, DiscoverFirstPageDto.serializer())?.let { c ->
                state = DiscoverUiState(
                    hotKeywords = c.hotKeywords,
                    ranks = c.ranks.map { it.toPlaylist() },
                    rankDetails = c.rankDetails.associate { it.rankId to it.detail.toPlaylistDetail() },
                    newMusic = c.newMusic.map { it.toMusic() },
                    hotArtists = c.hotArtists.map { it.toArtist() },
                )
                newMusicQueuePool = c.newMusicPool.map { it.toMusic() }
            }
            // 2) 新歌速递队列池与发现页数据并行刷新（pool 保留 DTO 以便存盘）
            val poolDeferred = scope.async {
                try {
                    api.fetchMusicList(1, 30, sort = "latest")
                } catch (_: Exception) {
                    emptyList<MusicDto>()
                }
            }
            try {
                // 所有一级请求并发：旧实现是逐个 await 的串行瀑布（关键词→榜单→5 个
                // 榜单详情→新歌→歌手），服务端稍慢时整页要十几秒，缓存都救不了刷新体验。
                val kwDeferred = scope.async {
                    try { api.fetchHotKeywords(8) } catch (_: Exception) { emptyList<HotKeyword>() }
                }
                val ranksDeferred = scope.async {
                    try { api.fetchRanks(1, 10) } catch (_: Exception) { emptyList<PlaylistDto>() }
                }
                val newMusicDeferred = scope.async {
                    try { api.fetchMusicList(1, 5, sort = "latest") } catch (_: Exception) { emptyList<MusicDto>() }
                }
                val artistDeferred = scope.async {
                    try { api.fetchArtists(1, 8) } catch (_: Exception) { emptyList<ArtistDto>() }
                }
                val kw = kwDeferred.await()
                val ranksDto = ranksDeferred.await()
                // 每个榜单只取前 4 首用于卡片预览（与 Android 端 RankCard 展示一致）。
                // 并发 + 逐个 catch：单个榜单详情失败不能拖垮整页（否则 state 不更新、缓存也不存）。
                val detailEntries = ranksDto.take(5).map { dto ->
                    scope.async {
                        try {
                            api.fetchRankDetail(dto.id, page = 1, limit = 4)?.let { detail ->
                                RankDetailEntry(rankId = dto.id, detail = detail)
                            }
                        } catch (e: Exception) {
                            println("FoxDiscoverScreen: 榜单 ${dto.id} 详情失败: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                // 与 Android DiscoverViewModel 对齐：新歌速递用 sort = "latest"
                val newMusicDto = newMusicDeferred.await()
                val artistDto = artistDeferred.await()
                state = DiscoverUiState(
                    hotKeywords = kw,
                    ranks = ranksDto.map { it.toPlaylist() },
                    rankDetails = detailEntries.associate { it.rankId to it.detail.toPlaylistDetail() },
                    newMusic = newMusicDto.map { it.toMusic() },
                    hotArtists = artistDto.map { it.toArtist() },
                )
                val details = detailEntries
                val poolDto = poolDeferred.await()
                newMusicQueuePool = poolDto.map { it.toMusic() }
                // 3) 存盘：下次冷启动直接命中（取到新数据后刷新缓存）
                FoxFirstPageCache.save(
                    cacheKey,
                    DiscoverFirstPageDto.serializer(),
                    DiscoverFirstPageDto(
                        hotKeywords = kw,
                        ranks = ranksDto,
                        rankDetails = details,
                        newMusic = newMusicDto,
                        hotArtists = artistDto,
                        newMusicPool = poolDto,
                    ),
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
                            is DiscoverIntent.OnMusicClick -> {
                                // 新歌速递整列表入队：优先用预取池（30 条），
                                // 屏幕上仍只展示前 5 条 —— 多加载但不展示完
                                val pool = newMusicQueuePool.ifEmpty { state.newMusic }
                                val (songs, index) = queueOf(pool, intent.music)
                                onMusicClickFromList(songs, index)
                            }
                            is DiscoverIntent.OnArtistClick ->
                                onArtistClick(intent.artist.id)
                            else -> Unit
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onSearchClick = onSearchClick,
                    onNewMusicMore = onNewMusicMore,
                    onHotArtistMore = onHotArtistMore,
                    onMusicMoreClick = onMusicMoreClick,
                    onOpenDrawer = onOpenDrawer,
                )
            }
        }
    }
}

/**
 * 歌单/榜单/专辑/收藏/历史 播放列表详情页 Presenter：
 * 复用 feature:playlist commonMain 的 PlaylistDetailContent —— 与 Android 完全同一份 UI
 * （大封面头部+播放全部/收藏+分页曲目），只是数据源按 [detailType] 切换：
 *
 *  - "playlist"       → /api/playlists/{id}（歌单广场/首页点入）
 *  - "rank"           → /api/playlists/ranks/{id}（发现页榜单点入）
 *  - "album"          → /api/albums/{id}（专辑详情）
 *  - "favorite_music" → /api/favorites/music（我的收藏，与 Android DetailType.FAVORITE_MUSIC 同语义）
 *  - "play_history"   → /api/music-history（历史播放）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxPlaylistDetailScreen {

    private val api = FoxMusicApi()

    /** 编辑保存/删除歌单后 Swift 调 [refresh]，详情页头部重载。 */
    var revision by mutableStateOf(0)
        private set

    fun refresh() {
        revision += 1
    }

    fun makePlaylistDetailViewController(
        playlistId: Long,
        useRankEndpoint: Boolean,
        detailType: String,
        onMusicClickFromList: (List<PlayableSong>, Int) -> Unit,
        onPlayAll: (List<PlayableSong>) -> Unit,
        onBack: () -> Unit,
        onMusicMoreClick: (Music) -> Unit = {},
        /** 管理菜单「编辑歌单」→ Swift 推/弹编辑页。 */
        onEditPlaylist: (Long) -> Unit = {},
        /** 管理菜单「删除歌单」确认后 → Swift 调删除接口。 */
        onDeletePlaylist: () -> Unit = {},
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val type = when (detailType) {
            "rank" -> DetailType.RANK
            "album" -> DetailType.ALBUM
            "favorite_music" -> DetailType.FAVORITE_MUSIC
            "play_history" -> DetailType.PLAY_HISTORY
            // 与 Android onRecommendClick / onNewMusicMore 的落地一致：
            // 虚拟歌单详情页（id 用 Long.MAX_VALUE / MAX-1 占位）
            "recommend" -> DetailType.RECOMMEND
            "new_music" -> DetailType.NEW_MUSIC
            else -> DetailType.PLAYLIST
        }
        var header by remember { mutableStateOf<HeaderInfo?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        // 仅自己的歌单显示管理菜单（编辑/删除），与 Android isOwner 同语义
        var isOwner by remember(playlistId) { mutableStateOf(false) }

        // 分页协程绑定本页生命周期：进入页面创建（带异常兜底），离开自动取消。
        // 之前这里是内联 MainScope()，每次进入/key 变化都新建一个永不取消的
        // 作用域（既泄漏协程，异常也没有 handler，Paging 抛错会直接 terminate 进程）。
        val pagingScope = remember { foxMainScope() }
        DisposableEffect(pagingScope) { onDispose { pagingScope.cancel() } }
        // 为你精选/新歌速递是「虚拟歌单」，一次要拉满 100 条（对齐 Android
        // getPagingSource(sort, maxTotalItems=100) + 当日 100 条快照）；
        // 其余类型（歌单/专辑/收藏/历史）仍按 30 条一页懒加载。
        val isTopList = type == DetailType.RECOMMEND || type == DetailType.NEW_MUSIC
        val tracksFlow = remember(playlistId, type, pagingScope) {
            val pagingSource: PagingSource<Int, Music> = when (type) {
                // 数据源与首页「为你精选」行同一份 100 条快照（FoxMusicTopListCache），
                // 这里 pageSize=100 一次取全：播放全部(100)、整份队列都到位。
                DetailType.RECOMMEND -> MusicTopListPagingSource(
                    api,
                    sort = FoxMusicTopListCache.SORT_RECOMMEND,
                )
                DetailType.NEW_MUSIC -> MusicTopListPagingSource(
                    api,
                    sort = FoxMusicTopListCache.SORT_LATEST,
                )
                DetailType.FAVORITE_MUSIC -> FavoriteMusicPagingSource(api)
                DetailType.PLAY_HISTORY -> PlayHistoryPagingSource(api)
                DetailType.ALBUM -> AlbumTracksPagingSource(api, playlistId)
                else -> PlaylistTracksPagingSource(api, playlistId, useRankEndpoint)
            }
            val pageSize = if (isTopList) FoxMusicTopListCache.MAX_ITEMS else 30
            Pager(PagingConfig(pageSize = pageSize, initialLoadSize = pageSize)) {
                pagingSource
            }.flow.cachedIn(pagingScope)
        }
        val pagingItems = tracksFlow.collectAsLazyPagingItems()

        LaunchedEffect(playlistId, type, revision) {
            try {
                when (type) {
                    // 与 Android PlaylistDetailViewModel 的 RECOMMEND/NEW_MUSIC 分支同文案：
                    // 虚拟头部（今日日期 + 固定标题），曲目列表由 sort 驱动
                    DetailType.RECOMMEND -> {
                        header = HeaderInfo(
                            id = Long.MAX_VALUE,
                            title = "为您定制推荐歌单",
                            coverImage = null,
                            description = null,
                            trackCount = 0,
                            creatorName = null,
                            createdAt = todayString(),
                            isFavorite = false,
                            detailType = DetailType.RECOMMEND,
                        )
                    }

                    DetailType.NEW_MUSIC -> {
                        header = HeaderInfo(
                            id = Long.MAX_VALUE - 1,
                            title = "最新歌曲为您呈现",
                            coverImage = null,
                            description = null,
                            trackCount = 0,
                            creatorName = null,
                            createdAt = todayString(),
                            isFavorite = false,
                            detailType = DetailType.NEW_MUSIC,
                        )
                    }

                    DetailType.ALBUM -> {
                        // AlbumDetailDto 没有现成的 toAlbum() 映射，这里按同字段手工转
                        // （AlbumDetailDto 与 AlbumDto 字段一致，仅少一个 favoritedAt）
                        api.fetchAlbumDetail(playlistId)?.let { d ->
                            header = HeaderInfo.fromAlbum(
                                Album(
                                    id = d.id,
                                    title = d.title,
                                    coverImage = d.coverImage,
                                    description = d.description,
                                    releaseDate = d.releaseDate,
                                    type = d.type,
                                    language = d.language,
                                    publisher = d.publisher,
                                    creatorId = d.creatorId,
                                    isPublic = d.isPublic,
                                    duration = d.duration,
                                    trackCount = d.trackCount,
                                    viewCount = d.viewCount,
                                    likeCount = d.likeCount,
                                    collectionCount = d.collectionCount,
                                    isFeatured = d.isFeatured,
                                    createdAt = d.createdAt,
                                    updatedAt = d.updatedAt,
                                    favoriteCount = d.favoriteCount,
                                    playCount = d.playCount,
                                    isFavorite = d.isFavorite,
                                    artists = d.artists.map { it.toArtist() },
                                ),
                            )
                        }
                    }

                    // 与 Android PlaylistDetailViewModel.loadHeader 的 FAVORITE_MUSIC/
                    // PLAY_HISTORY 分支同一口径：拿 total 当 trackCount，头部固定文案
                    DetailType.FAVORITE_MUSIC -> {
                        header = HeaderInfo(
                            id = DetailType.FAVORITE_MUSIC_ID,
                            title = "我的收藏",
                            coverImage = null,
                            description = "收藏的歌曲",
                            trackCount = api.fetchFavoriteMusics(1, 1).total,
                            creatorName = null,
                            createdAt = null,
                            isFavorite = false,
                            detailType = DetailType.FAVORITE_MUSIC,
                        )
                    }

                    DetailType.PLAY_HISTORY -> {
                        header = HeaderInfo(
                            id = DetailType.PLAY_HISTORY_ID,
                            title = "历史播放",
                            coverImage = null,
                            description = "最近听过的歌曲",
                            trackCount = api.fetchPlayHistoryPage(1, 1).total,
                            creatorName = null,
                            createdAt = null,
                            isFavorite = false,
                            detailType = DetailType.PLAY_HISTORY,
                        )
                    }

                    else -> {
                        val detail = if (useRankEndpoint) {
                            api.fetchRankDetail(playlistId)
                        } else {
                            api.fetchPlaylistDetail(playlistId)
                        }
                        header = detail?.let {
                            HeaderInfo.fromPlaylist(
                                it.toPlaylistDetail().playlist,
                                if (useRankEndpoint) DetailType.RANK else DetailType.PLAYLIST,
                            )
                        }
                        isOwner = detail?.creatorId != null &&
                                detail.creatorId == FoxAuthStore.user?.id
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }
        }

        // 编辑保存/导入加歌后：曲目列表跟着刷一次（头部由上面的 LaunchedEffect 重载）
        LaunchedEffect(revision) {
            if (revision > 0) pagingItems.refresh()
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "playlist-detail") {
                PlaylistDetailContent(
                    state = PlaylistDetailState(
                        headerInfo = header,
                        detailType = type,
                        isLoading = header == null && error == null,
                        error = error,
                    ),
                    pagingItems = pagingItems,
                    playlistKey = "playlist_${type}_$playlistId",
                    playlistId = playlistId,
                    // 自己的歌单才显示管理菜单（编辑/删除），与 Android isOwner 同语义
                    canManagePlaylist = type == DetailType.PLAYLIST && isOwner,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onMusicClick = { music ->
                        // 点某一首：整份已加载曲目当队列，并定位到点中的那首
                        val (songs, index) = queueOf(pagingItems.itemSnapshotList.items, music)
                        onMusicClickFromList(songs, index)
                    },
                    onPlayAll = {
                        val firstPage = pagingItems.itemSnapshotList.items
                        onPlayAll(firstPage.mapNotNull { FoxIosBridge.playableSong(it) })
                    },
                    onToggleFavorite = {}, // 需登录，后续批次接入
                    onMusicMoreClick = onMusicMoreClick,
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
                    onEditPlaylist = onEditPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                )
            }
        }
    }
}

/** 我的收藏歌曲分页源：GET api/favorites/music（与 Android FavoriteMusicPagingSource 同接口）。 */
internal class FavoriteMusicPagingSource(
    private val api: FoxMusicApi,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val page = params.key ?: 1
        val list = api.fetchFavoriteMusics(page, params.loadSize).list.map { it.toMusic() }
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

/** 历史播放分页源：GET api/music-history（后端强制登录）。 */
internal class PlayHistoryPagingSource(
    private val api: FoxMusicApi,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val page = params.key ?: 1
        val list = api.fetchPlayHistoryPage(page, params.loadSize)
            .list.map { it.toPlayHistory().music }
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

/** 专辑曲目分页源：GET api/albums/{id} 的 tracks 字段本身就是分页结构。 */
internal class AlbumTracksPagingSource(
    private val api: FoxMusicApi,
    private val albumId: Long,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val page = params.key ?: 1
        val list = api.fetchAlbumDetail(albumId, page, params.loadSize)
            ?.tracks?.list.orEmpty().map { it.toMusic() }
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

/**
 * iOS 端歌手详情页 Presenter：复用 feature:discover commonMain 的 ArtistDetailContent ——
 * 与 Android 歌手详情页同一份 UI（头像头部 + 热门歌曲 + 专辑）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxArtistDetailScreen {

    private val api = FoxMusicApi()

    fun makeArtistDetailViewController(
        artistId: Long,
        onMusicClick: (PlayableSong?) -> Unit,
        onAlbumClick: (Long) -> Unit,
        onBack: () -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        var detail by remember { mutableStateOf<ArtistDetail?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(artistId) {
            try {
                detail = api.fetchArtistDetail(artistId)?.toArtistDetail()
            } catch (e: Exception) {
                error = e.message
            }
        }

        val state = ArtistDetailState(
            artistDetail = detail,
            isLoading = detail == null && error == null,
            error = error,
        )

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "artist-detail") {
                ArtistDetailContent(
                    state = state,
                    onIntent = { intent ->
                        when (intent) {
                            ArtistDetailIntent.Load -> Unit // 数据在 LaunchedEffect 加载
                            ArtistDetailIntent.ToggleFavorite -> Unit // 需登录，后续批次接入
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onMusicClick = { onMusicClick(FoxIosBridge.playableSong(it)) },
                    onAlbumClick = { album -> onAlbumClick(album.id) },
                    onBack = onBack,
                )
            }
        }
    }
}

/** 虚拟歌单头部的「创建日期」：今天（与 Android TimeUtils.millis2String(now, "yyyy-MM-dd") 对齐）。 */
private fun todayString(): String = NSDateFormatter().apply {
    dateFormat = "yyyy-MM-dd"
}.stringFromDate(NSDate())

/**
 * 把"点中的一首歌 + 所在列表"折成"播放队列 + 起始下标"。
 *
 * 为什么需要这一步：Android 端点列表里任意一首都会把**整个列表**交给
 * MusicController（updatePlaylist）后播放，上一首/下一首因此可用；
 * iOS 早期只把单曲交给 AVPlayer，队列里永远只有一首。
 *
 * 兜底：万一列表里找不到目标（例如"最近在听"与"为你精选"是不同的数据源），
 * 就把这首追加到队尾并播放它，而不是错误地播放列表第一首。
 */
private fun queueOf(list: List<Music>, target: Music): Pair<List<PlayableSong>, Int> {
    val songs = list.mapNotNull { FoxIosBridge.playableSong(it) }
    val index = list.indexOfFirst { it.sourceId == target.sourceId }
    if (index >= 0 && songs.size == list.size) return songs to index
    val single = FoxIosBridge.playableSong(target)
    return if (single != null) (songs + single) to songs.size else songs to 0
}
