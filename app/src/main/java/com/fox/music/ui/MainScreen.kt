package com.fox.music.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.blankj.utilcode.util.ToastUtils
import com.fox.music.MainActivityViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CreatePlaylistBottomSheet
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.auth.ui.screen.LoginScreen
import com.fox.music.feature.chat.CHAT_ROUTE
import com.fox.music.feature.chat.ChatScreen
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.discover.DiscoverScreen
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.home.HomeScreen
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
import com.fox.music.feature.profile.PROFILE_ROUTE
import com.fox.music.feature.profile.ProfileScreen
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.feature.search.SearchScreen
import com.fox.music.feature.social.SOCIAL_ROUTE
import com.fox.music.feature.social.SocialScreen


@Composable
fun MainScreen(
    modifier: Modifier,
    navController: NavHostController,
    viewModel: MainActivityViewModel,
) {
    val musicController = viewModel.musicController
    val authState by viewModel.authState.collectAsState()
    val playerState by musicController.playerState.collectAsState(PlayerState())
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
    
    // 监听歌单状态变化
    val playlistState by viewModel.playlistState.collectAsState()
    
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
        musicController.setPlaylist(list, list.indexOf(music), key)
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
        showBottomBar: Boolean,
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
            if (showBottomBar) {
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
                composable(HOME_ROUTE) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
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
                            onSocialClick = {
                                navController.navigate(SOCIAL_ROUTE)
                            },
                            onRecommendClick = {
                                navController.navigate(
                                    playlistDetailRoute(
                                        Long.MAX_VALUE,
                                        DetailType.RECOMMEND
                                    )
                                )
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    }
                }
                composable(DISCOVER_ROUTE) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
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
                            }
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
                            onBack = {navController.popBackStack()}
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
                        showBottomBar,
                        this@SharedTransitionLayout
                    ) {
                        PlaylistDetailScreen(
                            it,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this,
                            onMusicClick = ::onMusicClick,
                            updateMusicList = ::onUpdateMusicList,
                            onBack = {navController.popBackStack()}
                        )
                    }
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        isLogin = authState.isLoggedIn,
                        onLogin = { navController.navigate(LOGIN_ROUTE) },
                        onPlaylistClick = { id ->
                            navController.navigate(playlistDetailRoute(id))
                        },
                        onCreatePlaylistClick = {
                            showCreatePlaylistBottomSheet = true
                        },
                        manageMusics = {
                            navController.navigate(MANAGE_ROUTER)
                        }
                    )
                }
                composable(MANAGE_ROUTER) {
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
                    LoginScreen(onNavigateToHome = {
                        navController.navigate(HOME_ROUTE) {
                            popUpTo(0) {
                                inclusive = true
                            }
                        }
                    })
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
                composable(SOCIAL_ROUTE) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
                    ) {
                        SocialScreen(it)
                    }
                }
                composable(CHAT_ROUTE) {
                    ChatScreen()
                }
            }
        }
    }

    // CreatePlaylistBottomSheet 弹窗
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
}