package com.fox.music.iosshared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.model.music.Playlist
import com.fox.music.feature.playlist.ui.component.PlaylistListContent
import com.fox.music.feature.playlist.viewmodel.PlaylistListState
import com.fox.music.iosshared.FoxMusicApi
import kotlinx.coroutines.MainScope
import platform.UIKit.UIViewController

/**
 * 推荐歌单的分页源：复用 feature:playlist commonMain 的 PlaylistListContent 渲染，
 * 数据走 Ktor（与 Android 端 PlaylistApiService.getRecommendedPlaylists 同一路径）。
 */
private class RecommendedPlaylistPagingSource(
    private val api: FoxMusicApi,
) : PagingSource<Int, Playlist>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Playlist> = try {
        val page = params.key ?: 1
        val list = api.fetchRecommendedPlaylists(page, params.loadSize).map { it.toPlaylist() }
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
 * iOS 端歌单广场 Presenter：状态生产完全在 Kotlin 侧（Paging flow），
 * Swift 只拿 UIViewController + 导航回调，不经手分页数据。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object FoxPlaylistScreen {

    private val scope = MainScope()
    private val api = FoxMusicApi()

    var error by mutableStateOf<String?>(null)
        private set

    private val pager = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
        RecommendedPlaylistPagingSource(api)
    }
    private val playlistFlow = pager.flow.cachedIn(scope)

    /**
     * 歌单广场页（共享 Compose 的 PlaylistListContent），交给 SwiftUI 外壳。
     * 目前只启用"推荐歌单"固定 Tab（categories 留空，后续接分类接口）。
     */
    fun makePlaylistListViewController(
        onPlaylistClick: (Playlist) -> Unit,
        onBackClick: () -> Unit = {},
    ): UIViewController = ComposeUIViewController {
        val state = PlaylistListState(
            categories = emptyList(),
            selectedCategoryIndex = 0,
            playlists = playlistFlow,
            isLoading = false,
            error = error,
        )
        // PlaylistListContent 需要 SharedTransitionScope/AnimatedContentScope，
        // 独立页面用 SharedTransitionLayout + 静态 AnimatedContent 提供（与 NavGraph 套法一致）。
        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "playlist-list") {
                PlaylistListContent(
                    state = state,
                    onIntent = {},
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent,
                    onPlaylistClick = onPlaylistClick,
                    onBackClick = onBackClick,
                )
            }
        }
    }
}
