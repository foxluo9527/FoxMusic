package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.assistant.assistantRoute
import com.fox.music.feature.social.COMMUNITY_PLAZA_ROUTE
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
import com.fox.music.feature.social.postDetailRoute
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
import com.fox.music.feature.profile.ui.screen.FAVORITE_DETAIL_ROUTE
import com.fox.music.feature.profile.ui.screen.FAVORITE_DETAIL_TYPE_ARG
import com.fox.music.feature.profile.ui.screen.FAVORITE_DETAIL_TYPE_ALBUMS
import com.fox.music.feature.profile.ui.screen.FAVORITE_DETAIL_TYPE_ARTISTS
import com.fox.music.feature.profile.ui.screen.FAVORITE_DETAIL_TYPE_PLAYLISTS
import com.fox.music.feature.profile.ui.screen.FavoriteCollectionDetailScreen
import com.fox.music.feature.profile.ui.screen.favoriteDetailRoute
import com.fox.music.feature.profile.ui.screen.userHomeRoute
import com.fox.music.feature.stream.StreamRoute
import com.fox.music.feature.stream.StreamRoutes
import com.fox.music.feature.stream.StreamSceneDrawerHost
import com.fox.music.feature.stream.StreamUiIntent
import com.fox.music.feature.stream.StreamViewModel
import com.fox.music.feature.player.ui.screen.sourceCorrectRoute

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
                    onOpenDrawer = context.onOpenDrawer,
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
                    onAssistantClick = {
                        // AI 助手全部依赖鉴权接口，未登录统一跳登录页（与消息 Tab 惯例一致）
                        if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                        else navController.navigate(assistantRoute())
                    },
                    onRecommendClick = {
                        navController.navigate(
                            playlistDetailRoute(
                                Long.MAX_VALUE,
                                DetailType.RECOMMEND
                            )
                        )
                    },
                    onRecentMoreClick = {
                        navController.navigate(
                            playlistDetailRoute(
                                DetailType.PLAY_HISTORY_ID,
                                DetailType.PLAY_HISTORY,
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
                    onOpenDrawer = context.onOpenDrawer,
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
                sharedTransitionScope = this@with,
            ) {
                CommunityPlazaScreen(
                    modifier = it,
                    refreshTrigger = context.plazaRefreshTrigger,
                    currentUserId = context.currentUserId,
                    showBackButton = false,
                    onOpenDrawer = context.onOpenDrawer,
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

    // 「消息」已从底部 Tab 移除，改为抽屉入口进入的二级页面（带返回栏，无底栏）。
    composableStack(MESSAGES_ROUTE) {
        MessagesScreen(
            modifier = Modifier.fillMaxSize(),
            showBackButton = true,
            onBack = { navController.popBackStack() },
            // 未登录门控：消息功能全部依赖鉴权接口，未登录点击统一跳登录页
            //（与社区 Tab 发帖/编辑帖的 navigate(LOGIN_ROUTE) 惯例一致）。
            onNavigateToFriends = {
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(FRIENDS_ROUTE)
            },
            onNavigateToCommentNotifications = {
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(notificationCategoryRoute("comment"))
            },
            onNavigateToLikeNotifications = {
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(notificationCategoryRoute("like"))
            },
            onNavigateToSystemAnnouncements = {
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(notificationCategoryRoute("system"))
            },
            onNavigateToNotificationSettings = {
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(NOTIFICATION_SETTINGS_ROUTE)
            },
            onNavigateToChat = { userId ->
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(chatDetailRoute(userId))
            },
            onNavigateToSearch = {
                if (!context.authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                else navController.navigate(chatSearchRoute())
            },
        )
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
                // 「我的」头部已改为纸感排版（不再自绘渐变），与其它 Tab 一样由脚手架统一让出状态栏。
                applyStatusBarsPadding = true,
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
                    onOpenDrawer = context.onOpenDrawer,
                    onMoreFavoritePlaylists = {
                        navController.navigate(favoriteDetailRoute(FAVORITE_DETAIL_TYPE_PLAYLISTS))
                    },
                    onMoreFavoriteAlbums = {
                        navController.navigate(favoriteDetailRoute(FAVORITE_DETAIL_TYPE_ALBUMS))
                    },
                    onMoreFavoriteArtists = {
                        navController.navigate(favoriteDetailRoute(FAVORITE_DETAIL_TYPE_ARTISTS))
                    },
                    currentUserId = context.currentUserId,
                    onPostDetail = { postId ->
                        navController.navigateToDetail(postDetailRoute(postId))
                    },
                    onSharePost = context.postActionsViewModel::sharePost,
                    onReportPost = context.postActionsViewModel::showReportSheet,
                    onDeletePost = context.postActionsViewModel::showDeleteDialog,
                    onEditPost = { postId ->
                        if (!context.authState.isLoggedIn) {
                            navController.navigate(LOGIN_ROUTE)
                        } else {
                            navController.navigate(createPostRoute(postId))
                        }
                    },
                    onTagClick = { tag ->
                        navController.navigate(
                            searchRoute(
                                tab = SearchResultTab.POST,
                                tag = tag,
                                sort = "hot",
                            ),
                        )
                    },
                    onPlayPostMusic = { post ->
                        post.music?.let { music ->
                            context.musicController.setPlaylist(
                                listOf(music),
                                0,
                                "post_${post.id}",
                                PlaybackPlaylistSource.fromPost(post),
                            )
                        }
                    },
                    onNavigateToPlayer = { navController.navigateToPlayer() },
                    playerState = playerState,
                )
            }
        }
    }

    // 音流模式（底部中央圆钮入口，作为 Tab 保留底栏以便随时切走）
    composableTab(StreamRoutes.STREAM) {
        val animatedScope = this
        // 音流状态在此层持有：顶栏三横按钮要打开抽屉，而抽屉必须渲染在底栏之上（见 overlayContent），
        // 两端需要共享同一份 ViewModel。
        // ViewModel 作用域提升到常驻的 HOME 导航栈（而非 STREAM 自身目的地）：切到其它 Tab 时 STREAM
        // 目的地会被出栈销毁，若用默认作用域 ViewModel 会被一并清理，回来再进入会新建实例 →
        // init 重新拉取并起播（换一批、从第 0 首开始），表现为「切回音流被重新加载」。
        // 挂到 HOME 栈上即可跨 Tab 复用同一实例：正在播放音流时切走再切回，状态保持、不重新加载。
        val streamViewModel: StreamViewModel = hiltViewModel(navController.getBackStackEntry(HOME_ROUTE))
        val sceneMood by streamViewModel.mood.collectAsStateWithLifecycle()
        var scenePanelOpen by remember { mutableStateOf(false) }
        // 抽屉打开时，系统返回键优先用于「关闭抽屉」，而不是直接退出音流回到首页
        BackHandler(enabled = scenePanelOpen) { scenePanelOpen = false }
        with(sharedTransitionScope) {
            MainScreenWithTabBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                tabRoute = StreamRoutes.STREAM,
                // 音流是沉浸式全屏封面：去掉底部 MiniPlayer 叠层，并让封面铺到状态栏。
                showMiniPlayer = false,
                sharedTransitionScope = this@with,
                applyStatusBarsPadding = false,
                overlayContent = {
                    StreamSceneDrawerHost(
                        open = scenePanelOpen,
                        onClose = { scenePanelOpen = false },
                        currentMood = sceneMood,
                        onSubmit = { nextMood ->
                            scenePanelOpen = false
                            streamViewModel.sendIntent(StreamUiIntent.Load(nextMood))
                        },
                    )
                },
            ) {
                StreamRoute(
                    modifier = it,
                    viewModel = streamViewModel,
                    onOpenScenePanel = { scenePanelOpen = true },
                    // App 内歌曲分享：复用全局音乐操作 → 好友选择流程（未登录先走登录页）
                    onShareMusic = { music ->
                        if (!context.authState.isLoggedIn) {
                            navController.navigate(LOGIN_ROUTE)
                        } else {
                            context.musicActionsViewModel.showMusicActions(music)
                            context.musicActionsViewModel.shareCurrentMusic()
                        }
                    },
                    // 管理员「音源纠错」：跳转纠错页替换当前歌曲音源
                    onNavigateToSourceCorrect = { musicId ->
                        navController.navigate(sourceCorrectRoute(musicId))
                    },
                )
            }
        }
    }

    // 收藏详情页（我的 → 音乐 → 收藏 → 更多）
    composableStack(
        FAVORITE_DETAIL_ROUTE,
        arguments = listOf(navArgument(FAVORITE_DETAIL_TYPE_ARG) { type = NavType.StringType }),
    ) { entry ->
        val type = entry.arguments?.getString(FAVORITE_DETAIL_TYPE_ARG)
            ?: FAVORITE_DETAIL_TYPE_PLAYLISTS
        FavoriteCollectionDetailScreen(
            type = type,
            onBack = { navController.popBackStack() },
            onPlaylistClick = { playlist ->
                val detailType = if (playlist.type.equals("rank", ignoreCase = true)) {
                    DetailType.RANK
                } else {
                    DetailType.PLAYLIST
                }
                navController.navigate(playlistDetailRoute(playlist.id, detailType))
            },
            onAlbumClick = { albumId ->
                navController.navigate(playlistDetailRoute(albumId, DetailType.ALBUM))
            },
            onArtistClick = context.onArtistClick,
        )
    }
}
