package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.core.ui.theme.foxTabPageBackground
import com.fox.music.feature.discover.ALL_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.ARTIST_DETAIL_ROUTE
import com.fox.music.feature.discover.ArtistDetailScreen
import com.fox.music.feature.discover.ArtistListScreen
import com.fox.music.feature.discover.HOT_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.HotArtistListScreen
import com.fox.music.feature.playlist.ui.component.PLAYLIST_LIST_ROUTE
import com.fox.music.feature.playlist.ui.component.PlaylistDetailScreen
import com.fox.music.feature.playlist.ui.component.PlaylistListScreen
import com.fox.music.feature.playlist.ui.screen.ALBUM_LIST_ROUTE
import com.fox.music.feature.playlist.ui.screen.AlbumListScreen
import com.fox.music.feature.playlist.ui.screen.PLAYLIST_EDIT_ROUTE
import com.fox.music.feature.playlist.ui.screen.PLAYLIST_IMPORT_DETAIL_ROUTE
import com.fox.music.feature.playlist.ui.screen.PlaylistEditScreen
import com.fox.music.feature.playlist.ui.screen.PlaylistImportDetailScreen
import com.fox.music.feature.playlist.ui.screen.playlistEditRoute
import com.fox.music.feature.playlist.ui.screen.playlistImportDetailRoute
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.feature.search.SearchScreen
import com.fox.music.feature.player.ui.screen.manageSearchRoute
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.PlaybackSourceKind
import com.fox.music.core.domain.toplist.TopListMusicStore
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.social.postDetailRoute
import com.fox.music.ui.importplaylist.IMPORT_PLAYLIST_SELECT_ROUTE
import com.fox.music.ui.importplaylist.ImportPlaylistSelectScreen

fun NavGraphBuilder.musicNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController
    val musicController = context.musicController
    val playerState = context.playerState
    val showBottomBar = context.showBottomBar

    composableStack(
        route = SEARCH_ROUTE,
        arguments = listOf(
            navArgument("tab") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("query") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("tag") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("sort") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                SearchScreen(
                    modifier = it,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onArtistClick = context.onArtistClick,
                    onAlbumClick = context.onAlbumClick,
                    onPostClick = { postId ->
                        navController.navigateToDetail(postDetailRoute(postId))
                    },
                    onMusicMoreClick = context.onMusicMoreClick,
                    onBack = { navController.popBackStack() },
                    // 「去收录」：跳转第三方平台搜索（添加单曲）页，带入当前关键词。
                    // fromSearch=true 表示本次收录完成后要一路返回到搜索页（见 CollectTrackScreen）
                    onCollectClick = { keyword ->
                        navController.navigate(manageSearchRoute(keyword, fromSearch = true))
                    },
                )
            }
        }
    }

    composableStack(PLAYLIST_LIST_ROUTE) {
        val animatedScope = this
        Box(
            modifier = Modifier
                .fillMaxSize()
                .foxTabPageBackground(),
        ) {
            with(sharedTransitionScope) {
                PlaylistListScreen(
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onPlaylistClick = context.onPlaylistClick,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    composableStack(ALBUM_LIST_ROUTE) {
        val animatedScope = this
        Box(
            modifier = Modifier
                .fillMaxSize()
                .foxTabPageBackground(),
        ) {
            with(sharedTransitionScope) {
                AlbumListScreen(
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onAlbumClick = context.onAlbumClick,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    composableStack(HOT_ARTIST_LIST_ROUTE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .foxTabPageBackground(),
        ) {
            HotArtistListScreen(
                modifier = Modifier.fillMaxSize(),
                onArtistClick = context.onArtistClick,
                onAllArtistsClick = {
                    navController.navigate(ALL_ARTIST_LIST_ROUTE)
                },
                onBackClick = { navController.popBackStack() },
            )
        }
    }

    composableStack(ALL_ARTIST_LIST_ROUTE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .foxTabPageBackground(),
        ) {
            ArtistListScreen(
                modifier = Modifier.fillMaxSize(),
                onArtistClick = context.onArtistClick,
                onBackClick = { navController.popBackStack() },
            )
        }
    }

    composableStack(
        route = ARTIST_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("artistId") { type = NavType.LongType },
        ),
    ) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                ArtistDetailScreen(
                    modifier = it,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onAlbumClick = context.onAlbumClick,
                    onBack = { navController.popBackStack() },
                    onMusicMoreClick = context.onMusicMoreClick,
                    onArtistClick = context.onArtistClick,
                    onViewAllArtistSongs = { artistId ->
                        navController.navigate(
                            playlistDetailRoute(artistId, DetailType.ARTIST)
                        )
                    },
                )
            }
        }
    }

    composableStack(
        route = "playlist_detail/{playlistId}/{type}",
        arguments = listOf(
            navArgument("playlistId") { type = NavType.StringType },
            navArgument("type") {
                type = NavType.StringType
                defaultValue = DetailType.PLAYLIST.name
            }
        ),
    ) {
        val animatedScope = this
        // 导入横幅点按 → 导入详情（逐首状态 + 失败曲目手动选音源）
        val detailPlaylistId = it.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar && !context.hideMiniPlayerInSelection,
                sharedTransitionScope = this@with,
            ) {
                PlaylistDetailScreen(
                    it,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onBack = { navController.popBackStack() },
                    onPlaylistDeleted = context.onProfileRefreshKeyIncrement,
                    onEditPlaylist = { id ->
                        navController.navigate(playlistEditRoute(id))
                    },
                    onMusicMoreClick = context.onMusicMoreClick,
                    onArtistClick = context.onArtistClick,
                    onAddSelectedToQueue = { musics ->
                        context.musicActionsViewModel.addAllToQueue(musics)
                    },
                    onAddSelectedToPlaylist = { musics ->
                        context.musicActionsViewModel.showAddToPlaylist(musics)
                    },
                    onDownloadSelected = { musics ->
                        context.musicActionsViewModel.downloadMusics(musics)
                    },
                    onSelectionModeChanged = context.onHideMiniPlayerInSelectionChange,
                    onTopListLoaded = { sort, list ->
                        // 详情页整份列表加载成功：回写首页/发现页的底层列表（展示条数不变），
                        // 若正在播放同一板块的队列则同步成最新整份列表
                        TopListMusicStore.publish(sort, list)
                        musicController.syncPlaylistForSource(
                            kind = if (sort == TopListMusicStore.SORT_RECOMMEND) {
                                PlaybackSourceKind.RECOMMEND
                            } else {
                                PlaybackSourceKind.NEW_MUSIC
                            },
                            musics = list,
                        )
                    },
                    onOpenImportDetail = {
                        if (detailPlaylistId > 0L) {
                            navController.navigate(playlistImportDetailRoute(detailPlaylistId))
                        }
                    },
                    onTracksRemoved = { musicIds, playlistKey ->
                        musicController.removeTracksFromPlaylistQueue(
                            musicIds = musicIds,
                            playlistKey = playlistKey,
                            playlistId = detailPlaylistId,
                        )
                    },
                )
            }
        }
    }

    // 导入详情是「盖上来的一层」：上滑进入、返回下滑消失（不走左右滑）
    composableSheetStack(
        route = PLAYLIST_IMPORT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("playlistId") { type = NavType.StringType },
        ),
    ) {
        PlaylistImportDetailScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(
        route = PLAYLIST_EDIT_ROUTE,
        arguments = listOf(
            navArgument("playlistId") { type = NavType.StringType },
        ),
    ) {
        PlaylistEditScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onSaved = context.onProfileRefreshKeyIncrement,
        )
    }

    composableStack(
        route = IMPORT_PLAYLIST_SELECT_ROUTE,
        arguments = listOf(
            navArgument("url") { type = NavType.StringType },
        ),
    ) {
        ImportPlaylistSelectScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlaylist = { playlistId ->
                navController.navigate(playlistDetailRoute(playlistId)) {
                    popUpTo(HOME_ROUTE) { inclusive = false }
                }
            },
            onMessage = { msg ->
                // Toast via utilcodex if available in app
                com.blankj.utilcode.util.ToastUtils.showShort(msg)
            },
        )
    }
}
