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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toPlaylistCategory
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistCategory
import com.fox.music.feature.playlist.ui.component.PlaylistListContent
import com.fox.music.feature.playlist.ui.screen.AlbumListContent
import com.fox.music.feature.playlist.viewmodel.AlbumListState
import com.fox.music.feature.playlist.viewmodel.PlaylistListState
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * 全部歌单页（歌单广场）Presenter：复用 feature:playlist commonMain 的 PlaylistListContent，
 * 与 Android PlaylistListScreen 同一份 UI（分类 Tab + 4 列网格 + 分页）。
 *
 * 数据逻辑与 Android PlaylistListViewModel 对齐：
 * - Tab 0「推荐歌单」→ GET api/playlists/recommended（categoryId=null）
 * - 其余 Tab → GET api/playlist-categories/{id}/playlists
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxPlaylistListScreen {

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    fun makePlaylistListViewController(
        onPlaylistClick: (Long, String) -> Unit,
        onBack: () -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        var categories by remember { mutableStateOf(emptyList<PlaylistCategory>()) }
        var selectedCategoryIndex by remember { mutableStateOf(0) }
        var loadError by remember { mutableStateOf<String?>(null) }

        val playlistsPager = remember(selectedCategoryIndex) {
            // 与 Android GetPlaylistsPagingUseCase 同配置：pageSize=20
            val categoryId = if (selectedCategoryIndex == 0) {
                null
            } else {
                categories.getOrNull(selectedCategoryIndex - 1)?.id
            }
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                PlaylistPagingSource(api, categoryId)
            }.flow.cachedIn(scope)
        }

        LaunchedEffect(Unit) {
            try {
                categories = api.fetchPlaylistCategories().map { it.toPlaylistCategory() }
            } catch (e: Exception) {
                loadError = e.message
            }
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "playlist-list") {
                PlaylistListContent(
                    state = PlaylistListState(
                        categories = categories,
                        selectedCategoryIndex = selectedCategoryIndex,
                        playlists = playlistsPager,
                        isLoading = categories.isEmpty() && loadError == null,
                        error = loadError,
                    ),
                    onIntent = { intent ->
                        when (intent) {
                            is com.fox.music.feature.playlist.viewmodel.PlaylistListIntent.SelectCategory ->
                                selectedCategoryIndex = intent.index
                            com.fox.music.feature.playlist.viewmodel.PlaylistListIntent.Load -> Unit // 分页自动加载
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onPlaylistClick = { playlist: Playlist ->
                        onPlaylistClick(playlist.id, playlist.title)
                    },
                    onBackClick = onBack,
                )
            }
        }
    }
}

/**
 * 歌单分页源：Tab 0 → 推荐歌单接口；其余 → 分类歌单分页接口。
 * 与 Android PlaylistPagingSource 同语义（下一页条件：本页满 loadSize）。
 */
internal class PlaylistPagingSource(
    private val api: FoxMusicApi,
    private val categoryId: Long?,
) : PagingSource<Int, Playlist>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Playlist> = try {
        val page = params.key ?: 1
        val list = if (categoryId == null) {
            api.fetchRecommendedPlaylists(page, params.loadSize)
        } else {
            api.fetchCategoryPlaylists(categoryId, page, params.loadSize).list
        }.map { it.toPlaylist() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Playlist>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

/**
 * 全部专辑页（推荐专辑）Presenter：复用 feature:playlist commonMain 的 AlbumListContent，
 * 与 Android AlbumListScreen 同一份 UI（TopAppBar + 4 列网格 + 分页）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxAlbumListScreen {

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    fun makeAlbumListViewController(
        onAlbumClick: (Long) -> Unit,
        onBack: () -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        // 与 Android GetAlbumListUseCase 同配置：pageSize=20
        val albumsPager = remember {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                AlbumListPagingSource(api)
            }.flow.cachedIn(scope)
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "album-list") {
                AlbumListContent(
                    state = AlbumListState(albums = albumsPager),
                    onIntent = { }, // Load 由分页自动触发
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onAlbumClick = { album: Album -> onAlbumClick(album.id) },
                    onBackClick = onBack,
                )
            }
        }
    }
}

/** 专辑分页源：GET api/albums?page&limit&sort=recommend（与 Android AlbumPagingSource 同接口）。
 * 推荐专辑页接后端 recommend 综合分（真实播放量+收藏+曲目数+运营精选），此前不传 sort 走的是最新上传。 */
internal class AlbumListPagingSource(
    private val api: FoxMusicApi,
) : PagingSource<Int, Album>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Album> = try {
        val page = params.key ?: 1
        val list = api.fetchAlbums(page, params.loadSize, sort = "recommend").map { it.toAlbum() }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size < params.loadSize) null else page + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Album>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
