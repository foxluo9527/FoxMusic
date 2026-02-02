package com.fox.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.player.MusicController
import com.fox.music.core.ui.components.MiniPlayer
import com.fox.music.feature.auth.LoginScreen
import com.fox.music.feature.auth.LOGIN_ROUTE
import com.fox.music.feature.chat.ChatScreen
import com.fox.music.feature.chat.CHAT_ROUTE
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.discover.DiscoverScreen
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.home.HomeScreen
import com.fox.music.feature.player.PLAYER_ROUTE
import com.fox.music.feature.player.PlayerScreen
import com.fox.music.feature.playlist.PlaylistDetailScreen
import com.fox.music.feature.playlist.PlaylistListScreen
import com.fox.music.feature.playlist.playlistDetailRoute
import com.fox.music.feature.playlist.PLAYLIST_LIST_ROUTE
import com.fox.music.feature.profile.PROFILE_ROUTE
import com.fox.music.feature.profile.ProfileScreen
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.feature.search.SearchScreen
import com.fox.music.feature.social.SOCIAL_ROUTE
import com.fox.music.feature.social.SocialScreen
import com.fox.music.ui.theme.FoxMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoxMusicTheme {
                FoxMusicApp()
            }
        }
    }
}

enum class AppDestinations(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(HOME_ROUTE, "Home", Icons.Default.Home),
    DISCOVER(DISCOVER_ROUTE, "Discover", Icons.Default.Explore),
    SEARCH(SEARCH_ROUTE, "Search", Icons.Default.Search),
    PLAYLIST(PLAYLIST_LIST_ROUTE, "Playlist", Icons.Default.PlaylistPlay),
    PROFILE(PROFILE_ROUTE, "Profile", Icons.Default.AccountBox),
}

@Composable
fun FoxMusicApp() {
    val viewModel: MainActivityViewModel = hiltViewModel()

    val authState by viewModel.authState.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    val navigationSuiteState = rememberNavigationSuiteScaffoldState()
    // Check if user is logged in and not on login screen
    LaunchedEffect(authState.isLoggedIn, currentRoute) {
        if (AppDestinations.entries.map { it.route }.contains(currentRoute)) {
            navigationSuiteState.show()
        } else {
            navigationSuiteState.hide()
        }
        if (!authState.isLoggedIn && currentRoute != LOGIN_ROUTE) {
            navController.navigate(LOGIN_ROUTE) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (AppDestinations.entries.map { it.route }.contains(currentRoute)) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { dest ->
                    item(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            selectedDestination = dest
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(HOME_ROUTE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) {
            MainScreen(Modifier.fillMaxSize(), navController, currentRoute, viewModel)
        }
    } else {
        MainScreen(Modifier.fillMaxSize(), navController, currentRoute, viewModel)
    }
}


@Composable
fun MainScreen(
    modifier: Modifier,
    navController: NavHostController,
    currentRoute: String?,
    viewModel: MainActivityViewModel
) {
    val musicController = viewModel.musicController
    val playerState by musicController.playerState.collectAsState(com.fox.music.core.model.PlayerState())
    fun onMusicClick(music: Music) {
        musicController.setPlaylist(listOf(music), 0)
        musicController.play()
        navController.navigate(PLAYER_ROUTE)
    }

    fun onPlaylistClick(playlist: Playlist) {
        navController.navigate(playlistDetailRoute(playlist.id))
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (playerState.currentMusic != null && currentRoute != PLAYER_ROUTE) {
                MiniPlayer(
                    playerState = playerState,
                    onPlayPauseClick = { if (playerState.isPlaying) musicController.pause() else musicController.play() },
                    onNextClick = { musicController.next() },
                    onClick = { navController.navigate(PLAYER_ROUTE) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(HOME_ROUTE) {
                    HomeScreen(
                        onMusicClick = ::onMusicClick,
                        onPlaylistClick = ::onPlaylistClick
                    )
                }
                composable(DISCOVER_ROUTE) {
                    DiscoverScreen(
                        onMusicClick = ::onMusicClick,
                        onPlaylistClick = ::onPlaylistClick
                    )
                }
                composable(SEARCH_ROUTE) {
                    SearchScreen(onMusicClick = ::onMusicClick)
                }
                composable(PLAYLIST_LIST_ROUTE) {
                    PlaylistListScreen(onPlaylistClick = ::onPlaylistClick)
                }
                composable(
                    route = "playlist_detail/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) {
                    PlaylistDetailScreen(onMusicClick = ::onMusicClick)
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen()
                }
                composable(LOGIN_ROUTE) {
                    LoginScreen(onNavigateToHome = {
                        navController.navigate(HOME_ROUTE) {
                            popUpTo(0) {
                                inclusive = true
                            }
                        }
                    })
                }
                composable(PLAYER_ROUTE) {
                    PlayerScreen(
                        musicController = musicController,
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