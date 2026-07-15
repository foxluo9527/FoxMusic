package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import com.fox.music.core.model.music.DetailType
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.social.ui.screen.COMMUNITY_PLAZA_ROUTE
import com.fox.music.feature.discover.HOT_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.DiscoverScreen
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.home.HomeScreen
import com.fox.music.feature.playlist.ui.component.PLAYLIST_LIST_ROUTE
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.playlist.ui.screen.ALBUM_LIST_ROUTE
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.ProfileScreen
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.feature.social.ui.screen.createPostRoute
import com.fox.music.feature.social.ui.screen.postDetailRoute
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.chat.MESSAGES_ROUTE
import com.fox.music.feature.playlist.ui.screen.playlistEditRoute
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.profile.ui.screen.SETTINGS_ROUTE

fun NavGraphBuilder.tabNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController
    val musicController = context.musicController
    val playerState = context.playerState
    val showBottomBar = context.showBottomBar

    composableTab(HOME_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = HOME_ROUTE,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                HomeScreen(
                    modifier = it,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onPlaylistClick = { id ->
                        navController.navigate(playlistDetailRoute(id))
                    },
                    onAlbumClick = { id ->
                        navController.navigate(playlistDetailRoute(id, DetailType.ALBUM))
                    },
                    onPlaylistCategoryClick = {
                        navController.navigate(PLAYLIST_LIST_ROUTE)
                    },
                    onAlbumCategoryClick = {
                        navController.navigate(ALBUM_LIST_ROUTE)
                    },
                    onSearchClick = {
                        navController.navigate(SEARCH_ROUTE)
                    },
                    onRecommendClick = {
                        navController.navigate(
                            playlistDetailRoute(
                                Long.MAX_VALUE,
                                DetailType.RECOMMEND
                            )
                        )
                    },
                    onMusicMoreClick = context.onMusicMoreClick,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                )
            }
        }
    }

    composableTab(DISCOVER_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = DISCOVER_ROUTE,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                DiscoverScreen(
                    modifier = it,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onRankClick = { id ->
                        navController.navigate(
                            playlistDetailRoute(
                                id,
                                type = DetailType.RANK
                            )
                        )
                    },
                    onSearchClick = {
                        navController.navigate(SEARCH_ROUTE)
                    },
                    onNewMusicMore = {
                        navController.navigate(
                            playlistDetailRoute(
                                Long.MAX_VALUE - 1,
                                type = DetailType.NEW_MUSIC
                            )
                        )
                    },
                    onHotArtistMore = {
                        navController.navigate(HOT_ARTIST_LIST_ROUTE)
                    },
                    onCommunityPlazaMore = {
                        navController.navigate(COMMUNITY_PLAZA_ROUTE)
                    },
                    currentUserId = context.currentUserId,
                    onPostDetail = { postId ->
                        navController.navigate(postDetailRoute(postId))
                    },
                    onSharePost = context.postActionsViewModel::sharePost,
                    onEditPost = { postId ->
                        navController.navigate(createPostRoute(postId))
                    },
                    onReportPost = context.postActionsViewModel::showReportSheet,
                    onDeletePost = context.postActionsViewModel::showDeleteDialog,
                    onArtistClick = context.onArtistClick,
                    onMusicMoreClick = context.onMusicMoreClick,
                )
            }
        }
    }

    composableTab(PROFILE_ROUTE) {
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = PROFILE_ROUTE,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                ProfileScreen(
                    modifier = it,
                    isLogin = context.authState.isLoggedIn,
                    refreshKey = context.profileRefreshKey,
                    onLogin = { navController.navigate(LOGIN_ROUTE) },
                    onPlaylistClick = { id ->
                        navController.navigate(playlistDetailRoute(id))
                    },
                    onFavoriteTracksClick = {
                        navController.navigate(
                            playlistDetailRoute(
                                DetailType.FAVORITE_MUSIC_ID,
                                DetailType.FAVORITE_MUSIC,
                            )
                        )
                    },
                    onPlayHistoryClick = {
                        navController.navigate(
                            playlistDetailRoute(
                                DetailType.PLAY_HISTORY_ID,
                                DetailType.PLAY_HISTORY,
                            )
                        )
                    },
                    onFavoritePlaylistClick = { playlist ->
                        val type = if (playlist.type.equals("rank", ignoreCase = true)) {
                            DetailType.RANK
                        } else {
                            DetailType.PLAYLIST
                        }
                        navController.navigate(playlistDetailRoute(playlist.id, type))
                    },
                    onAlbumClick = { albumId ->
                        navController.navigate(playlistDetailRoute(albumId, DetailType.ALBUM))
                    },
                    onCreatePlaylistClick = context.showCreatePlaylistBottomSheet,
                    onArtistClick = context.onArtistClick,
                    onSettingsClick = {
                        navController.navigate(SETTINGS_ROUTE)
                    },
                    onMessageNotificationClick = {
                        navController.navigate(MESSAGES_ROUTE)
                    },
                    onDownloadManagerClick = {
                        navController.navigate(DOWNLOAD_MANAGER_ROUTE)
                    },
                    onPlayAllPlaylist = { playlistId, musicList ->
                        if (musicList.isNotEmpty()) {
                            context.onMusicClick(
                                musicList.first(),
                                musicList,
                                "collection_detail/$playlistId",
                            )
                        }
                    },
                    onMusicClick = { music, list ->
                        context.onMusicClick(music, list, PROFILE_ROUTE)
                    },
                    onEditPlaylistClick = { id ->
                        navController.navigate(playlistEditRoute(id))
                    },
                )
            }
        }
    }
}
