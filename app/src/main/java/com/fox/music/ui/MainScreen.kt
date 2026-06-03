package com.fox.music.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.blankj.utilcode.util.ToastUtils
import com.fox.music.MainActivityViewModel
import com.fox.music.realtime.BackgroundPermissionGuide
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CreatePlaylistBottomSheet
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.core.ui.component.UpdateDialog
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.auth.ui.screen.LoginScreen
import com.fox.music.feature.chat.ADD_FRIEND_ROUTE
import com.fox.music.feature.chat.CHAT_DETAIL_ROUTE
import com.fox.music.feature.chat.CHAT_SEARCH_ROUTE
import com.fox.music.feature.chat.CHAT_SETTINGS_ROUTE
import com.fox.music.feature.chat.FRIENDS_ROUTE
import com.fox.music.feature.chat.MESSAGES_ROUTE
import com.fox.music.feature.chat.NOTIFICATION_CATEGORY_ROUTE
import com.fox.music.feature.chat.SEARCH_USER_ROUTE
import com.fox.music.feature.chat.SELECT_FRIEND_ROUTE
import com.fox.music.feature.chat.USER_CHAT_SEARCH_ROUTE
import com.fox.music.feature.chat.USER_PROFILE_ROUTE
import com.fox.music.feature.chat.addFriendRoute
import com.fox.music.feature.chat.chatDetailRoute
import com.fox.music.feature.chat.chatSearchRoute
import com.fox.music.feature.chat.chatSettingsRoute
import com.fox.music.feature.chat.notificationCategoryRoute
import com.fox.music.feature.chat.searchUserRoute
import com.fox.music.feature.chat.selectFriendRoute
import com.fox.music.feature.chat.ui.screen.AddFriendRequestScreen
import com.fox.music.feature.chat.ui.screen.ChatDetailScreen
import com.fox.music.feature.chat.ui.screen.ChatSearchScreen
import com.fox.music.feature.chat.ui.screen.ChatSettingsScreen
import com.fox.music.feature.chat.ui.screen.FriendsScreen
import com.fox.music.feature.chat.ui.screen.MessagesScreen
import com.fox.music.feature.chat.ui.screen.NotificationCategoryScreen
import com.fox.music.feature.chat.ui.screen.SearchUserScreen
import com.fox.music.feature.chat.ui.screen.SelectFriendScreen
import com.fox.music.feature.chat.ui.screen.UserChatSearchScreen
import com.fox.music.feature.chat.ui.screen.UserProfileScreen
import com.fox.music.feature.chat.userChatSearchRoute
import com.fox.music.feature.chat.userProfileRoute
import com.fox.music.feature.discover.ALL_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.ARTIST_DETAIL_ROUTE
import com.fox.music.feature.discover.ArtistDetailScreen
import com.fox.music.feature.discover.ArtistListScreen
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.discover.DiscoverScreen
import com.fox.music.feature.discover.HOT_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.HotArtistListScreen
import com.fox.music.feature.discover.artistDetailRoute
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.home.HomeScreen
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.fox.music.feature.player.ui.screen.MANAGE_ROUTER
import com.fox.music.feature.player.ui.screen.ManageScreen
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.player.ui.screen.PlayerScreen
import com.fox.music.feature.playlist.ui.component.PLAYLIST_LIST_ROUTE
import com.fox.music.feature.playlist.ui.component.PlaylistDetailScreen
import com.fox.music.feature.playlist.ui.component.PlaylistListScreen
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.playlist.ui.screen.ALBUM_LIST_ROUTE
import com.fox.music.feature.playlist.ui.screen.AlbumListScreen
import com.fox.music.feature.playlist.ui.screen.PLAYLIST_EDIT_ROUTE
import com.fox.music.feature.playlist.ui.screen.PlaylistEditScreen
import com.fox.music.feature.playlist.ui.screen.playlistEditRoute
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.profile.ui.screen.DownloadScreen
import com.fox.music.feature.profile.ui.screen.EDIT_PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.EditProfileScreen
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.ProfileScreen
import com.fox.music.feature.profile.ui.screen.REPORT_HISTORY_ROUTE
import com.fox.music.feature.profile.ui.screen.ReportHistoryScreen
import com.fox.music.feature.profile.ui.screen.SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.SettingsScreen
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.feature.search.SearchScreen
import com.fox.music.update.ApkInstallHelper
import com.fox.music.update.ApkInstallResult
import com.fox.music.ui.InAppNotificationHost

@OptIn(UnstableApi::class)
@Composable
fun MainScreen(
    modifier: Modifier,
    navController: NavHostController,
    viewModel: MainActivityViewModel,
    deepLinkRoute: String = HOME_ROUTE,
    musicActionsViewModel: MusicActionsViewModel = hiltViewModel(),
) {
    val musicController = viewModel.musicController
    val authState by viewModel.authState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val showBottomBar = remember(
        playerState.currentMusic
    ) { playerState.currentMusic != null }
    
    // 弹窗状态管理
    var showCreatePlaylistBottomSheet by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var inputDialogType by remember { mutableStateOf("create") } // "create" or "import"
    var inputValue by remember { mutableStateOf("") }
    var inputDialogTitle by remember { mutableStateOf("") }
    var inputDialogLabel by remember { mutableStateOf("") }
    var profileRefreshKey by remember { mutableIntStateOf(0) }
    var hideMiniPlayerInSelection by remember { mutableStateOf(false) }

    // 监听歌单状态变化
    val playlistState by viewModel.playlistState.collectAsState()

    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute.isNotBlank() && deepLinkRoute != HOME_ROUTE) {
            navController.navigate(deepLinkRoute) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(authState.requireReLogin) {
        if (authState.requireReLogin) {
            ToastUtils.showLong("登录已失效，请重新登录")
            navController.navigate(LOGIN_ROUTE) {
                popUpTo(HOME_ROUTE) { inclusive = false }
                launchSingleTop = true
            }
            viewModel.onRequireReLoginHandled()
        }
    }

    // 监听从 SelectFriendScreen 返回的好友选择结果
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        val entry = currentEntry ?: return@LaunchedEffect
        val friendId = entry.savedStateHandle.get<Long>("selectedFriendId") ?: return@LaunchedEffect
        entry.savedStateHandle.remove<Long>("selectedFriendId")
        entry.savedStateHandle.remove<String>("selectedFriendNickname")
        entry.savedStateHandle.remove<String>("selectedFriendAvatar")

        when {
            // 音乐分享流程
            musicActionsViewModel.uiState.value.showSelectFriend -> {
                musicActionsViewModel.sendShareMusic(friendId)
            }
            // 转发流程：将 friendId 写入 ChatDetail 的 savedStateHandle
            else -> {
                try {
                    val chatDetailEntry = navController.getBackStackEntry(CHAT_DETAIL_ROUTE)
                    chatDetailEntry.savedStateHandle["forwardFriendId"] = friendId
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(updateState.installFile) {
        val apkFile = updateState.installFile ?: return@LaunchedEffect
        val result = ApkInstallHelper.tryInstall(context, apkFile)
        viewModel.onInstallAttemptFinished(
            launched = result is ApkInstallResult.Launched,
            file = apkFile,
        )
    }

    DisposableEffect(lifecycleOwner, updateState.pendingInstallApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = viewModel.updateState.value.pendingInstallApk ?: return@LifecycleEventObserver
                if (!ApkInstallHelper.canInstallPackages(context)) return@LifecycleEventObserver
                val result = ApkInstallHelper.tryInstall(context, pending)
                viewModel.onInstallAttemptFinished(
                    launched = result is ApkInstallResult.Launched,
                    file = pending,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(updateState.pendingInstallApk) {
        if (updateState.pendingInstallApk != null) {
            ToastUtils.showShort("请允许安装权限后返回应用继续安装")
        }
    }

    LaunchedEffect(updateState.noUpdateMessage) {
        updateState.noUpdateMessage?.let { message ->
            ToastUtils.showShort(message)
            viewModel.onNoUpdateMessageHandled()
        }
    }

    // 当歌单创建或导入成功后，刷新页面
    LaunchedEffect(playlistState) {
        if (playlistState.isPlaylistCreated || playlistState.isPlaylistImported) {
            // 重置歌单状态
            viewModel.resetPlaylistState()
            // 刷新页面，这里可以通过重新导航到当前页面来实现
            navController.navigate(PROFILE_ROUTE) {
                popUpTo(PROFILE_ROUTE) {
                    inclusive = true
                }
            }
        }else if (playlistState.error !=null){
            ToastUtils.showLong(playlistState.error)
        }
    }

    fun onMusicClick(music: Music, list: List<Music>, key: String) {
        val playlist = list.takeIf { it.isNotEmpty() } ?: listOf(music)
        val startIndex = playlist.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
        musicController.setPlaylist(playlist, startIndex, key)
        navController.navigate(PLAYER_ROUTE)
    }

    fun onUpdateMusicList(musicList: List<Music>, key: String) {
        musicController.updatePlaylist(musicList, key)
    }

    fun onPlaylistClick(playlist: Playlist) {
        navController.navigate(playlistDetailRoute(playlist.id))
    }

    fun onAlbumClick(album: Album) {
        navController.navigate(playlistDetailRoute(album.id, DetailType.ALBUM))
    }

    fun onArtistClick(artistId: Long) {
        navController.navigate(artistDetailRoute(artistId))
    }

    fun onMusicMoreClick(music: Music) {
        musicActionsViewModel.showMusicActions(music)
    }

    // 弹窗处理函数
    fun showCreatePlaylistBottomSheet() {
        showCreatePlaylistBottomSheet = true
    }

    fun onCreateCustomPlaylist() {
        showCreatePlaylistBottomSheet = false
        inputDialogType = "create"
        inputDialogTitle = "创建自定义歌单"
        inputDialogLabel = "歌单名称"
        inputValue = ""
        showInputDialog = true
    }

    fun onImportThirdPartyPlaylist() {
        showCreatePlaylistBottomSheet = false
        inputDialogType = "import"
        inputDialogTitle = "导入第三方歌单"
        inputDialogLabel = "歌单链接"
        inputValue = ""
        showInputDialog = true
    }

    fun onInputDialogConfirm() {
        if (inputValue.isNotBlank()) {
            if (inputDialogType == "create") {
                // 调用创建歌单的方法
                viewModel.createPlaylist(inputValue)
            } else {
                // 调用导入歌单的方法
                viewModel.importPlaylist(inputValue)
            }
            showInputDialog = false
            inputValue = ""
        }
    }

    fun onInputDialogDismiss() {
        showInputDialog = false
        inputValue = ""
    }

    @Composable
    fun AnimatedContentScope.MainScreenWithBottomBar(
        showMiniPlayer: Boolean,
        sharedTransitionScope: SharedTransitionScope,
        navScreen: @Composable AnimatedContentScope.(Modifier) -> Unit,
    ) {
        Column {
            navScreen(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
            )
            if (showMiniPlayer) {
                MiniPlayer(
                    playerState = playerState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this@MainScreenWithBottomBar,
                    onPlayPauseClick = { musicController.togglePlay() },
                    onNextClick = { musicController.next() },
                    onClick = { navController.navigate(PLAYER_ROUTE) }
                )
            }
        }
    }

    /**
     * Tab 根页面布局：内容 + 迷你播放器 + 底部 Tab 栏。
     * Tab 栏内嵌在 NavHost 目的地中，可随页面转场与可预测返回一起动画，避免回到首页后突然出现。
     */
    @Composable
    fun AnimatedContentScope.MainScreenWithTabBar(
        tabRoute: String,
        showMiniPlayer: Boolean,
        sharedTransitionScope: SharedTransitionScope,
        navScreen: @Composable AnimatedContentScope.(Modifier) -> Unit,
    ) {
        Column(Modifier.fillMaxSize()) {
            navScreen(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
            )
            if (showMiniPlayer) {
                MiniPlayer(
                    playerState = playerState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this@MainScreenWithTabBar,
                    onPlayPauseClick = { musicController.togglePlay() },
                    onNextClick = { musicController.next() },
                    onClick = { navController.navigate(PLAYER_ROUTE) }
                )
            }
            AppBottomNavigationBar(
                navController = navController,
                selectedRoute = tabRoute,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        SharedTransitionLayout(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    route = HOME_ROUTE,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) },
                ) {
                    MainScreenWithTabBar(
                        tabRoute = HOME_ROUTE,
                        showMiniPlayer = showBottomBar,
                        sharedTransitionScope = this@SharedTransitionLayout,
                    ) {
                        HomeScreen(
                            modifier = it,
                            onMusicClick = ::onMusicClick,
                            updateMusicList = ::onUpdateMusicList,
                            onPlaylistClick = {id ->
                                navController.navigate(playlistDetailRoute(id))
                            },
                            onAlbumClick = {id ->
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
                            onMusicMoreClick = ::onMusicMoreClick,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    }
                }
                composable(
                    route = DISCOVER_ROUTE,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) },
                ) {
                    MainScreenWithTabBar(
                        tabRoute = DISCOVER_ROUTE,
                        showMiniPlayer = showBottomBar,
                        sharedTransitionScope = this@SharedTransitionLayout,
                    ) {
                        DiscoverScreen(
                            modifier = it,
                            onMusicClick = ::onMusicClick,
                            updateMusicList = ::onUpdateMusicList,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this,
                            onRankClick = {id ->
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
                                        Long.MAX_VALUE-1,
                                        type = DetailType.NEW_MUSIC
                                    )
                                )
                            },
                            onHotArtistMore = {
                                navController.navigate(HOT_ARTIST_LIST_ROUTE)
                            },
                            onArtistClick = ::onArtistClick,
                            onMusicMoreClick = ::onMusicMoreClick,
                        )
                    }
                }
                composable(SEARCH_ROUTE) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
                    ) {
                        SearchScreen(
                            modifier = it,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this,
                            onMusicClick = ::onMusicClick,
                            updateMusicList = ::onUpdateMusicList,
                            onArtistClick = ::onArtistClick,
                            onAlbumClick = ::onAlbumClick,
                            onMusicMoreClick = ::onMusicMoreClick,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = PLAYLIST_LIST_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    PlaylistListScreen(
                        modifier = Modifier.fillMaxSize(),
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this,
                        onPlaylistClick = ::onPlaylistClick,
                        onBackClick = {navController.popBackStack()}
                    )
                }
                composable(
                    route = ALBUM_LIST_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    AlbumListScreen(
                        modifier = Modifier.fillMaxSize(),
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this,
                        onAlbumClick = ::onAlbumClick,
                        onBackClick = {navController.popBackStack()}
                    )
                }
                composable(
                    route = HOT_ARTIST_LIST_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    HotArtistListScreen(
                        modifier = Modifier.fillMaxSize(),
                        onArtistClick = ::onArtistClick,
                        onAllArtistsClick = {
                            navController.navigate(ALL_ARTIST_LIST_ROUTE)
                        },
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(
                    route = ALL_ARTIST_LIST_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    ArtistListScreen(
                        modifier = Modifier.fillMaxSize(),
                        onArtistClick = ::onArtistClick,
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(
                    route = ARTIST_DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument("artistId") { type = NavType.LongType },
                    ),
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
                    ) {
                        ArtistDetailScreen(
                            modifier = it,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this,
                            onMusicClick = ::onMusicClick,
                            updateMusicList = ::onUpdateMusicList,
                            onAlbumClick = ::onAlbumClick,
                            onBack = { navController.popBackStack() },
                            onMusicMoreClick = ::onMusicMoreClick,
                            onArtistClick = ::onArtistClick,
                        )
                    }
                }
                composable(
                    route = "playlist_detail/{playlistId}/{type}",
                    arguments = listOf(
                        navArgument("playlistId") {type = NavType.StringType},
                        navArgument("type") {
                            type = NavType.StringType
                            defaultValue = DetailType.PLAYLIST.name
                        }
                    ), enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    }, exitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    MainScreenWithBottomBar(
                        showBottomBar && !hideMiniPlayerInSelection,
                        this@SharedTransitionLayout
                    ) {
                        PlaylistDetailScreen(
                            it,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this,
                            onMusicClick = ::onMusicClick,
                            updateMusicList = ::onUpdateMusicList,
                            onBack = { navController.popBackStack() },
                            onPlaylistDeleted = { profileRefreshKey++ },
                            onEditPlaylist = { id ->
                                navController.navigate(playlistEditRoute(id))
                            },
                            onMusicMoreClick = ::onMusicMoreClick,
                            onArtistClick = ::onArtistClick,
                            onAddSelectedToQueue = { musics ->
                                musicActionsViewModel.addAllToQueue(musics)
                            },
                            onAddSelectedToPlaylist = { musicIds ->
                                musicActionsViewModel.showAddToPlaylist(musicIds)
                            },
                            onDownloadSelected = { musics ->
                                musicActionsViewModel.downloadMusics(musics)
                            },
                            onSelectionModeChanged = { hideMiniPlayerInSelection = it },
                        )
                    }
                }
                composable(
                    route = PLAYLIST_EDIT_ROUTE,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType },
                    ),
                ) {
                    PlaylistEditScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onSaved = { profileRefreshKey++ },
                    )
                }
                composable(
                    route = PROFILE_ROUTE,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) },
                ) {
                    MainScreenWithTabBar(
                        tabRoute = PROFILE_ROUTE,
                        showMiniPlayer = showBottomBar,
                        sharedTransitionScope = this@SharedTransitionLayout,
                    ) {
                        ProfileScreen(
                            modifier = it,
                            isLogin = authState.isLoggedIn,
                            refreshKey = profileRefreshKey,
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
                            onCreatePlaylistClick = {
                                showCreatePlaylistBottomSheet = true
                            },
                            onArtistClick = ::onArtistClick,
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
                                    onMusicClick(
                                        musicList.first(),
                                        musicList,
                                        "collection_detail/$playlistId",
                                    )
                                }
                            },
                            onEditPlaylistClick = { id ->
                                navController.navigate(playlistEditRoute(id))
                            },
                        )
                    }
                }
                composable(
                    route = SETTINGS_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    val settingsContext = LocalContext.current
                    val lyricSyncManager = remember { LyricSyncManager.getInstance() }
                    val isDesktopLyricEnabled by lyricSyncManager.isDesktopLyricEnabled.collectAsState()
                    SettingsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        isDesktopLyricEnabled = isDesktopLyricEnabled,
                        onDesktopLyricChange = { enabled ->
                            if (enabled) {
                                lyricSyncManager.openDesktopLyric(settingsContext)
                            } else {
                                lyricSyncManager.toggleDesktopLyric()
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onEditProfile = { navController.navigate(EDIT_PROFILE_ROUTE) },
                        onManageLibrary = { navController.navigate(MANAGE_ROUTER) },
                        onDownloadManager = { navController.navigate(DOWNLOAD_MANAGER_ROUTE) },
                        onReportHistory = { navController.navigate(REPORT_HISTORY_ROUTE) },
                        onInstallApk = viewModel::requestInstall,
                        onLogout = {
                            navController.navigate(LOGIN_ROUTE) {
                                popUpTo(HOME_ROUTE) { inclusive = false }
                            }
                        },
                    )
                }
                composable(
                    route = DOWNLOAD_MANAGER_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
                    ) {
                        DownloadScreen(
                            modifier = it,
                            onBack = { navController.popBackStack() },
                            onNavigateToPlayer = { navController.navigate(PLAYER_ROUTE) },
                        )
                    }
                }
                composable(
                    route = REPORT_HISTORY_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    ReportHistoryScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = EDIT_PROFILE_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    EditProfileScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onProfileSaved = { profileRefreshKey++ },
                    )
                }
                composable(
                    route = MANAGE_ROUTER,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    ManageScreen(Modifier.fillMaxSize().statusBarsPadding()){
                        navController.popBackStack()
                    }
                }
                composable(LOGIN_ROUTE, enterTransition = {
                    slideIntoContainer(
                        towards = SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                }, exitTransition = {
                    slideOutOfContainer(
                        towards = SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }) {
                    val loginContext = LocalContext.current
                    var showBackgroundGuide by remember { mutableStateOf(false) }
                    val navigateHomeFromLogin = {
                        navController.navigate(HOME_ROUTE) {
                            popUpTo(HOME_ROUTE) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                    if (showBackgroundGuide) {
                        BackgroundPermissionGuideDialog(
                            onLater = {
                                showBackgroundGuide = false
                                navigateHomeFromLogin()
                            },
                            onConfirmed = {
                                showBackgroundGuide = false
                                navigateHomeFromLogin()
                            },
                        )
                    }
                    LoginScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToHome = {
                            if (BackgroundPermissionGuide.shouldShowAfterLogin(loginContext)) {
                                showBackgroundGuide = true
                            } else {
                                navigateHomeFromLogin()
                            }
                        },
                    )
                }
                composable(PLAYER_ROUTE, enterTransition = {
                    slideIntoContainer(
                        towards = SlideDirection.Up,
                        animationSpec = tween(700)
                    )
                }, exitTransition = {
                    slideOutOfContainer(
                        towards = SlideDirection.Down,
                        animationSpec = tween(700)
                    )
                }) {
                    PlayerScreen(
                        musicController = musicController,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = MESSAGES_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    MessagesScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
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
                        onNavigateToChat = { userId ->
                            navController.navigate(chatDetailRoute(userId))
                        },
                        onNavigateToSearch = {
                            navController.navigate(chatSearchRoute())
                        },
                    )
                }
                composable(
                    route = FRIENDS_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    FriendsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onNavigateToSearch = { navController.navigate(searchUserRoute()) },
                        onNavigateToUserProfile = { userId, nickname, avatar, signature, isFriend, isRequested ->
                            navController.navigate(
                                userProfileRoute(
                                    userId = userId,
                                    nickname = nickname,
                                    avatar = avatar,
                                    signature = signature,
                                    isFriend = isFriend,
                                    isRequested = isRequested,
                                ),
                            )
                        },
                    )
                }
                composable(SEARCH_USER_ROUTE) {
                    SearchUserScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onNavigateToUserProfile = { userId, nickname, avatar, signature, isFriend, isRequested ->
                            navController.navigate(
                                userProfileRoute(
                                    userId = userId,
                                    nickname = nickname,
                                    avatar = avatar,
                                    signature = signature,
                                    isFriend = isFriend,
                                    isRequested = isRequested,
                                ),
                            )
                        },
                    )
                }
                composable(
                    route = USER_PROFILE_ROUTE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.LongType },
                        navArgument("nickname") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("avatar") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("signature") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("isFriend") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("isRequested") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { backStackEntry ->
                    val friendRequestSent by backStackEntry.savedStateHandle
                        .getStateFlow("friendRequestSent", false)
                        .collectAsStateWithLifecycle()
                    UserProfileScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        friendRequestSent = friendRequestSent,
                        onFriendRequestSentHandled = {
                            backStackEntry.savedStateHandle["friendRequestSent"] = false
                        },
                        onBack = { navController.popBackStack() },
                        onNavigateToChat = { userId, peerNickname, peerAvatar ->
                            navController.navigate(chatDetailRoute(userId, peerNickname, peerAvatar))
                        },
                        onNavigateToAddFriend = { userId, nickname ->
                            navController.navigate(addFriendRoute(userId, nickname))
                        },
                    )
                }
                composable(
                    route = ADD_FRIEND_ROUTE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.LongType },
                        navArgument("nickname") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    AddFriendRequestScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onRequestSent = {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("friendRequestSent", true)
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    route = NOTIFICATION_CATEGORY_ROUTE,
                    arguments = listOf(navArgument("type") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    NotificationCategoryScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = CHAT_DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.LongType },
                        navArgument("peerNickname") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("peerAvatar") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    ChatDetailScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onNavigateToSettings = { userId, nickname, avatar ->
                            navController.navigate(chatSettingsRoute(userId, nickname, avatar))
                        },
                        onForward = { message ->
                            navController.navigate(selectFriendRoute())
                        },
                        onShareClick = { message, allMessages ->
                            val shareId = message.shareId ?: return@ChatDetailScreen
                            when (message.shareType) {
                                "music" -> {
                                    message.shareData?.let { data ->
                                        // 收集聊天中所有音乐分享消息，构建播放列表
                                        val musicList = allMessages
                                            .filter { it.type == MessageType.SHARE && it.shareType == "music" && it.shareData != null }
                                            .mapNotNull { msg ->
                                                msg.shareData?.let { sd ->
                                                    Music(
                                                        id = sd.id,
                                                        title = sd.title ?: "",
                                                        url = sd.url ?: "",
                                                        coverImage = sd.coverImage,
                                                        duration = sd.duration,
                                                        artists = sd.artists,
                                                        lyrics = sd.lyrics,
                                                        lyricsTrans = sd.lyricsTrans,
                                                        lyricsUrl = sd.lyricsUrl,
                                                    )
                                                }
                                            }
                                            .ifEmpty {
                                                listOf(
                                                    Music(
                                                        id = data.id,
                                                        title = data.title ?: "",
                                                        url = data.url ?: "",
                                                        coverImage = data.coverImage,
                                                        duration = data.duration,
                                                        artists = data.artists,
                                                        lyrics = data.lyrics,
                                                        lyricsTrans = data.lyricsTrans,
                                                        lyricsUrl = data.lyricsUrl,
                                                    )
                                                )
                                            }
                                        val startIndex = musicList.indexOfFirst { it.id == data.id }.coerceAtLeast(0)
                                        musicController.setPlaylist(musicList, startIndex, "chat_music")
                                        navController.navigate(PLAYER_ROUTE)
                                    }
                                }
                                "playlist" -> navController.navigate(playlistDetailRoute(shareId))
                                "album" -> navController.navigate(playlistDetailRoute(shareId, DetailType.ALBUM))
                                "artist" -> navController.navigate(artistDetailRoute(shareId))
                            }
                        },
                    )
                }
                composable(
                    route = SELECT_FRIEND_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    SelectFriendScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onFriendSelected = { friend ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("selectedFriendId", friend.id)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("selectedFriendNickname", friend.mark ?: friend.nickname ?: friend.username ?: "")
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("selectedFriendAvatar", friend.avatar ?: "")
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    route = CHAT_SETTINGS_ROUTE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.LongType },
                        navArgument("peerNickname") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("peerAvatar") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    ChatSettingsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onNavigateToUserProfile = { userId, nickname, avatar, signature, isFriend, isRequested ->
                            navController.navigate(
                                userProfileRoute(
                                    userId = userId,
                                    nickname = nickname,
                                    avatar = avatar,
                                    signature = signature,
                                    isFriend = isFriend,
                                    isRequested = isRequested,
                                ),
                            )
                        },
                        onNavigateToChatSearch = { userId, nickname, avatar ->
                            navController.navigate(userChatSearchRoute(userId, nickname, avatar, ""))
                        },
                    )
                }
                composable(
                    route = CHAT_SEARCH_ROUTE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    ChatSearchScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onNavigateToUserSearch = { userId, nickname, avatar, query ->
                            navController.navigate(userChatSearchRoute(userId, nickname, avatar, query))
                        },
                        onNavigateToChatDetail = { userId, messageId ->
                            navController.navigate(chatDetailRoute(userId))
                        },
                        onNavigateToUserProfile = { userId ->
                            navController.navigate(userProfileRoute(userId))
                        },
                    )
                }
                composable(
                    route = USER_CHAT_SEARCH_ROUTE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.LongType },
                        navArgument("nickname") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("avatar") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("query") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = {
                        slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        ExitTransition.None
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    UserChatSearchScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        onBack = { navController.popBackStack() },
                        onNavigateToChatDetail = { userId, messageId ->
                            navController.navigate(chatDetailRoute(userId))
                        },
                        onNavigateToUserProfile = { userId ->
                            navController.navigate(userProfileRoute(userId))
                        },
                    )
                }
            }
        }
    }

    // CreatePlaylistBottomSheet 弹窗
    InAppNotificationHost(navController = navController)

    MusicActionsHost(
        onArtistClick = ::onArtistClick,
        onCreatePlaylist = ::showCreatePlaylistBottomSheet,
        onNavigateToSelectFriend = {
            navController.navigate(selectFriendRoute())
        },
        viewModel = musicActionsViewModel,
    )

    if (showCreatePlaylistBottomSheet) {
        CreatePlaylistBottomSheet(
            onDismiss = { showCreatePlaylistBottomSheet = false },
            onCreateCustom = ::onCreateCustomPlaylist,
            onImportThirdParty = ::onImportThirdPartyPlaylist
        )
    }

    // 输入弹窗
    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = ::onInputDialogDismiss,
            title = { Text(inputDialogTitle) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(inputDialogLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = ::onInputDialogConfirm,
                    enabled = inputValue.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = ::onInputDialogDismiss) {
                    Text("取消")
                }
            }
        )
    }

    val updateInfo = updateState.updateInfo
    if (updateState.showDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo,
            forceUpdate = updateState.forceUpdate,
            isDownloading = updateState.isDownloading,
            downloadProgress = updateState.downloadProgress,
            downloadIndeterminate = updateState.downloadIndeterminate,
            downloadStatusText = updateState.downloadStatusText,
            error = updateState.error,
            onDismiss = viewModel::onDismissUpdate,
            onConfirmUpdate = viewModel::onConfirmUpdate,
            onRetry = viewModel::retryDownload,
        )
    }
}