package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.PlaybackPlaylistSource
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
import com.fox.music.feature.search.SearchResultTab
import com.fox.music.feature.search.searchRoute
import com.fox.music.feature.social.ui.screen.CommunityPlazaScreen
import com.fox.music.feature.social.ui.screen.POST_PUBLISH_QUEUE_ROUTE
import com.fox.music.feature.social.ui.screen.createPostRoute
import com.fox.music.feature.social.ui.screen.postDetailRoute
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.chat.MESSAGES_ROUTE
import com.fox.music.feature.chat.FRIENDS_ROUTE
import com.fox.music.feature.chat.NOTIFICATION_SETTINGS_ROUTE
import com.fox.music.feature.chat.chatDetailRoute
import com.fox.music.feature.chat.chatSearchRoute
import com.fox.music.feature.chat.notificationCategoryRoute
import com.fox.music.feature.chat.ui.screen.MessagesScreen
import com.fox.music.feature.playlist.ui.screen.playlistEditRoute
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.profile.ui.screen.SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.userHomeRoute

fun NavGraphBuilder.tabNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController
    val musicController = context.musicController
    val playerState = context.playerState
    val showBottomBar = context.showBottomBar
    val messagesUnreadCount = context.messagesUnreadCount

    composableTab(HOME_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = HOME_ROUTE,
                showMiniPlayer = showBottomBar,
                messagesUnreadCount = messagesUnreadCount,
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
                        navController.navigate(searchRoute())
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
                messagesUnreadCount = messagesUnreadCount,
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
                        navController.navigate(searchRoute())
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
                    onArtistClick = context.onArtistClick,
                    onMusicMoreClick = context.onMusicMoreClick,
                )
            }
        }
    }

    composableTab(COMMUNITY_PLAZA_ROUTE) {
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = COMMUNITY_PLAZA_ROUTE,
                showMiniPlayer = showBottomBar,
                messagesUnreadCount = messagesUnreadCount,
                sharedTransitionScope = this@with,
            ) {
                CommunityPlazaScreen(
                    modifier = it,
                    refreshTrigger = context.plazaRefreshTrigger,
                    currentUserId = context.currentUserId,
                    showBackButton = false,
                    onSearchClick = {
                        navController.navigate(searchRoute(SearchResultTab.POST))
                    },
                    onCreatePost = {
                        if (!context.authState.isLoggedIn) {
                            navController.navigate(LOGIN_ROUTE)
                        } else {
                            navController.navigate(createPostRoute())
                        }
                    },
                    onPublishQueue = {
                        if (!context.authState.isLoggedIn) {
                            navController.navigate(LOGIN_ROUTE)
                        } else {
                            navController.navigate(POST_PUBLISH_QUEUE_ROUTE)
                        }
                    },
                    onPostDetail = { postId ->
                        navController.navigateToDetail(postDetailRoute(postId))
                    },
                    onSharePost = context.postActionsViewModel::sharePost,
                    onEditPost = { postId ->
                        if (!context.authState.isLoggedIn) {
                            navController.navigate(LOGIN_ROUTE)
                        } else {
                            navController.navigate(createPostRoute(postId))
                        }
                    },
                    onReportPost = context.postActionsViewModel::showReportSheet,
                    onDeletePost = context.postActionsViewModel::showDeleteDialog,
                    onPlayMusic = { post ->
                        post.music?.let { music ->
                            context.musicController.setPlaylist(
                                listOf(music),
                                0,
                                "post_${post.id}",
                                PlaybackPlaylistSource.fromPost(post),
                            )
                        }
                    },
                    onTogglePlay = { musicController.togglePlay() },
                    onNavigateToPlayer = { navController.navigateToPlayer() },
                    playerState = playerState,
                    onTagClick = { tag ->
                        navController.navigate(
                            searchRoute(
                                tab = SearchResultTab.POST,
                                tag = tag,
                                sort = "hot",
                            ),
                        )
                    },
                )
            }
        }
    }

    composableTab(MESSAGES_ROUTE) {
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = MESSAGES_ROUTE,
                showMiniPlayer = showBottomBar,
                messagesUnreadCount = messagesUnreadCount,
                sharedTransitionScope = this@with,
            ) {
                MessagesScreen(
                    modifier = it,
                    showBackButton = false,
                    onNavigateToFriends = { navController.navigate(FRIENDS_ROUTE) },
                    onNavigateToCommentNotifications = {
                        navController.navigate(notificationCategoryRoute("comment"))
                    },
                    onNavigateToLikeNotifications = {
                        navController.navigate(notificationCategoryRoute("like"))
                    },
                    onNavigateToSystemAnnouncements = {
                        navController.navigate(notificationCategoryRoute("system"))
                    },
                    onNavigateToNotificationSettings = {
                        navController.navigate(NOTIFICATION_SETTINGS_ROUTE)
                    },
                    onNavigateToChat = { userId ->
                        navController.navigate(chatDetailRoute(userId))
                    },
                    onNavigateToSearch = {
                        navController.navigate(chatSearchRoute())
                    },
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
                messagesUnreadCount = messagesUnreadCount,
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
                    onUserHomeClick = {
                        context.currentUserId?.let { userId ->
                            navController.navigate(userHomeRoute(userId))
                        }
                    },
                    onDownloadManagerClick = {
                        navController.navigate(DOWNLOAD_MANAGER_ROUTE)
                    },
                    onPlayAllPlaylist = { playlistId, musicList, title, coverUrl ->
                        if (musicList.isNotEmpty()) {
                            context.onMusicClick(
                                musicList.first(),
                                musicList,
                                "collection_detail/$playlistId",
                                PlaybackPlaylistSource.fromDetailType(
                                    type = DetailType.PLAYLIST,
                                    id = playlistId,
                                    title = title,
                                    coverUrl = coverUrl,
                                ),
                            )
                        }
                    },
                    onMusicClick = { music, list ->
                        context.onMusicClick(music, list, PROFILE_ROUTE, null)
                    },
                    onEditPlaylistClick = { id ->
                        navController.navigate(playlistEditRoute(id))
                    },
                )
            }
        }
    }
}
