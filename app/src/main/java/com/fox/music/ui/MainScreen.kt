package com.fox.music.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fox.music.MainActivityViewModel
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
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
    val playerState by musicController.playerState.collectAsState(com.fox.music.core.model.PlayerState())
    val showBottomBar = remember(
        playerState.currentMusic
    ) { playerState.currentMusic != null }

    fun onMusicClick(music: Music, list: List<Music>, key: String) {
        musicController.setPlaylist(list, list.indexOf(music), key)
        musicController.play()
        navController.navigate(PLAYER_ROUTE)
    }

    fun onUpdateMusicList(musicList: List<Music>, key: String) {
        musicController.updatePlaylist(musicList, key)
    }

    fun onPlaylistClick(playlist: Playlist) {
        navController.navigate(playlistDetailRoute(playlist.id))
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
        modifier = modifier
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
                            onPlaylistClick = ::onPlaylistClick
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
                        )
                    }
                }
                composable(PLAYLIST_LIST_ROUTE) {
                    MainScreenWithBottomBar(
                        showBottomBar,
                        this@SharedTransitionLayout
                    ) {
                        PlaylistListScreen(
                            modifier = it,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this,
                            isLogin = authState.isLoggedIn,
                            onPlaylistClick = ::onPlaylistClick,
                            onLogin = { navController.navigate(LOGIN_ROUTE) }
                        )
                    }
                }
                composable(
                    route = "playlist_detail/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) {
                    PlaylistDetailScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this,
                        onMusicClick = ::onMusicClick,
                        updateMusicList = ::onUpdateMusicList,
                    )
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen(isLogin = authState.isLoggedIn,onLogin = { navController.navigate(LOGIN_ROUTE) }){
                        navController.navigate(MANAGE_ROUTER)
                    }
                }
                composable(MANAGE_ROUTER) {
                    ManageScreen(Modifier.fillMaxSize()){
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
                        animationSpec = tween(500)
                    )
                }, exitTransition = {
                    slideOutOfContainer(
                        towards = SlideDirection.Down,
                        animationSpec = tween(500)
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
                    SocialScreen()
                }
                composable(CHAT_ROUTE) {
                    ChatScreen()
                }
            }
        }
    }
}