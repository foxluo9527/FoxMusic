package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toListeningStats
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toUser
import com.fox.music.feature.profile.ui.screen.ProfileContent
import com.fox.music.feature.profile.viewmodel.ProfileIntent
import com.fox.music.feature.profile.viewmodel.ProfileState
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.FoxPlaylistRefresh
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

object FoxProfileScreen {

    private val api = FoxMusicApi()
    private val scope = foxMainScope()

    /** 拉取歌单全部曲目（播放全部用，分页拉全，上限 500 首防失控）。 */
    private suspend fun fetchPlaylistTracks(playlistId: Long): List<com.fox.music.core.model.music.Music> {
        val result = mutableListOf<com.fox.music.core.model.music.Music>()
        var page = 1
        while (page <= 10) {
            val detail = api.fetchPlaylistDetail(playlistId, page, 50) ?: break
            val list = detail.tracks.list
            result += list.map { it.toMusic() }
            if (list.isEmpty() || result.size >= detail.tracks.total) break
            page++
        }
        return result
    }

    fun makeProfileViewController(
        onLogin: () -> Unit,
        /**
         * 右上角齿轮：无论是否登录都进设置页。
         * 之前是 `if (loggedIn) onLogout() else onLogin()` —— 游客点齿轮弹登录、
         * 已登录点齿轮直接退出登录，两个都不对（ProfileContent 的登录态分支还把齿轮
         * 接到了 onLogin）。设置页里的「退出登录」才是退出入口（与 Android 一致）。
         */
        onSettings: () -> Unit,
        onPlaylistClick: (Long, String) -> Unit,
        onArtistClick: (Long) -> Unit,
        onAlbumClick: (Long) -> Unit,
        /** 「我的收藏」歌曲列表 → Swift 推列表详情页（PlaylistDetailContent 承载，与 Android 同一份 UI）。 */
        onFavoriteTracksClick: () -> Unit,
        /** 「历史播放」歌曲列表 → Swift 推列表详情页。 */
        onPlayHistoryClick: () -> Unit,
        /** 左上角三横「更多」→ Swift 打开左侧抽屉。 */
        onOpenDrawer: () -> Unit = {},
        /** 收藏分区「更多歌单/专辑/艺术家」→ Swift 推收藏详情页。 */
        onMoreFavoritePlaylists: () -> Unit = {},
        onMoreFavoriteAlbums: () -> Unit = {},
        onMoreFavoriteArtists: () -> Unit = {},
        /** 「动态」Tab 帖子点击 → Swift 推帖子详情。 */
        onPostDetail: (Long) -> Unit = {},
        /** 帖子分享/举报 → Swift 弹分享面板 / 举报面板。 */
        onSharePost: (Long, String?) -> Unit = { _, _ -> },
        onReportPost: (Long, String?) -> Unit = { _, _ -> },
        /** 帖子编辑 → Swift 推编辑页。 */
        onEditPost: (Long) -> Unit = {},
        /** 「下载管理」行 → Swift 推原生 DownloadManagerView（与 Android 我的页入口对齐）。 */
        onDownloadManagerClick: () -> Unit = {},
        /** 「创建歌单」入口 → Swift 弹统一 BottomSheet（导入第三方 / 自定义创建）。 */
        onCreatePlaylistClick: () -> Unit = {},
        /** 歌单管理菜单「编辑歌单」→ Swift 推编辑页。 */
        onEditPlaylistClick: (Long) -> Unit = {},
        /** 歌单管理菜单「全部播放」→ Swift 起播。 */
        onPlayAll: (List<PlayableSong>) -> Unit = {},
        /** Kotlin 侧产生的提示（如删除结果）→ Swift Toast。 */
        onMessage: (String) -> Unit = {},
        /** 头部资料卡点击 → Swift 推自己的个人主页（对齐 Android onUserHomeClick）。 */
        onUserHome: () -> Unit = {},
    ): UIViewController = foxComposeViewController {
        val authRevision = FoxAuthStore.revision
        // 创建/删除/编辑歌单后 Swift 调 FoxPlaylistRefresh.refreshProfile() 触发重载
        val playlistRevision = FoxPlaylistRefresh.profileRevision
        val loggedIn = FoxAuthStore.isLoggedIn
        val currentUserId = FoxAuthStore.user?.id
        var state by remember { mutableStateOf(ProfileState()) }

        LaunchedEffect(authRevision, playlistRevision) {
            if (!FoxAuthStore.isLoggedIn) {
                state = ProfileState()
                return@LaunchedEffect
            }
            state = state.copy(isLoading = true, error = null)
            try {
                val user = api.fetchProfile()?.toUser()
                val playlists = try {
                    api.fetchUserPlaylists().map { it.toPlaylist() }
                } catch (_: Exception) {
                    emptyList()
                }
                val historyPage = try {
                    api.fetchPlayHistoryPage(1, 1)
                } catch (_: Exception) {
                    null
                }
                val stats = try {
                    api.fetchListeningStats(7)?.toListeningStats()
                } catch (_: Exception) {
                    null
                }
                val favMusic = try {
                    api.fetchFavoriteMusics(1, 50)
                } catch (_: Exception) {
                    null
                }
                val favPlaylists = try {
                    api.fetchFavoritePlaylists(1, 50)
                } catch (_: Exception) {
                    null
                }
                val favAlbums = try {
                    api.fetchFavoriteAlbums(1, 50)
                } catch (_: Exception) {
                    null
                }
                val favArtists = try {
                    api.fetchFavoriteArtists(1, 50)
                } catch (_: Exception) {
                    null
                }
                state = ProfileState(
                    user = user,
                    playlists = playlists,
                    favoriteMusicTotal = favMusic?.total ?: 0,
                    favoritePlaylists = favPlaylists?.list.orEmpty().map { it.toPlaylist() },
                    favoritePlaylistTotal = favPlaylists?.total ?: 0,
                    favoriteAlbums = favAlbums?.list.orEmpty().map { it.toAlbum() },
                    favoriteAlbumTotal = favAlbums?.total ?: 0,
                    favoriteArtists = favArtists?.list.orEmpty().map { it.toArtist() },
                    favoriteArtistTotal = favArtists?.total ?: 0,
                    playHistoryTotal = historyPage?.total ?: 0,
                    listeningStats = stats,
                    isLoading = false,
                    error = if (user == null) "无法加载资料，请重新登录" else null,
                )
            } catch (e: Exception) {
                state = ProfileState(isLoading = false, error = e.message ?: "加载失败")
            }
        }

        // 「动态」Tab：自己的帖子分页（未登录时不给 Pager，ProfileContent 走游客态不渲染）
        val postsFlow = remember(currentUserId) {
            currentUserId?.let { uid ->
                Pager(
                    config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 20),
                    pagingSourceFactory = { PostPagingSource(api, authorId = uid) },
                ).flow.cachedIn(scope)
            }
        }
        val postPagingItems = postsFlow?.collectAsLazyPagingItems()

        ProfileContent(
            state = state,
            onIntent = { intent ->
                when (intent) {
                    is ProfileIntent.OnPlaylistClick ->
                        onPlaylistClick(intent.playlist.id, intent.playlist.title)
                    ProfileIntent.OnFavoriteTracksClick -> onFavoriteTracksClick()
                    // 歌单管理菜单「删除歌单」：确认框在 Compose 侧，这里只管真删 + 刷新
                    is ProfileIntent.DeletePlaylist -> {
                        scope.launch {
                            try {
                                api.deletePlaylist(intent.playlistId)
                                onMessage("歌单已删除")
                            } catch (e: Exception) {
                                onMessage(e.message?.takeIf { it.isNotBlank() } ?: "删除歌单失败")
                            }
                            FoxPlaylistRefresh.refreshProfile()
                        }
                    }
                    // 歌单管理菜单「全部播放」：分页拉全曲目后整队起播
                    is ProfileIntent.PlayAllPlaylist -> {
                        scope.launch {
                            try {
                                val songs = fetchPlaylistTracks(intent.playlistId)
                                    .mapNotNull { FoxIosBridge.playableSong(it) }
                                if (songs.isEmpty()) {
                                    onMessage("歌单还没有歌曲")
                                } else {
                                    onPlayAll(songs)
                                }
                            } catch (e: Exception) {
                                onMessage(e.message?.takeIf { it.isNotBlank() } ?: "播放失败")
                            }
                        }
                    }
                    else -> Unit
                }
            },
            isLogin = loggedIn,
            onOpenDrawer = onOpenDrawer,
            onLogin = onLogin,
            onFavoritePlaylistClick = { onPlaylistClick(it.id, it.title) },
            onAlbumClick = onAlbumClick,
            onPlayHistoryClick = onPlayHistoryClick,
            onDownloadManagerClick = onDownloadManagerClick,
            onArtistClick = onArtistClick,
            onSettingsClick = onSettings,
            onCreatePlaylistFromVm = onCreatePlaylistClick,
            onEditPlaylistClick = onEditPlaylistClick,
            onUserHomeClick = onUserHome,
            onMoreFavoritePlaylists = onMoreFavoritePlaylists,
            onMoreFavoriteAlbums = onMoreFavoriteAlbums,
            onMoreFavoriteArtists = onMoreFavoriteArtists,
            postPagingItems = postPagingItems,
            currentUserId = currentUserId,
            onPostDetail = onPostDetail,
            onSharePost = { post ->
                onSharePost(post.id, post.title?.takeIf { it.isNotBlank() } ?: post.content.take(50))
            },
            onReportPost = { post ->
                onReportPost(post.id, post.title?.takeIf { it.isNotBlank() } ?: "未命名帖子")
            },
            onDeletePost = { post ->
                scope.launch {
                    try {
                        api.deletePost(post.id)
                        onMessage("动态已删除")
                        postPagingItems?.refresh()
                    } catch (e: Exception) {
                        onMessage(e.message?.takeIf { it.isNotBlank() } ?: "删除失败")
                    }
                }
            },
            onEditPost = onEditPost,
        )
    }
}
