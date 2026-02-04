package com.fox.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.playlist.PLAYLIST_LIST_ROUTE
import com.fox.music.feature.profile.PROFILE_ROUTE
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.ui.MainScreen
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
    var showBottomNavBar by remember {mutableStateOf(true)}

    LaunchedEffect(authState.isLoggedIn) {
        if (!authState.isLoggedIn && currentRoute != LOGIN_ROUTE) {
            navController.navigate(LOGIN_ROUTE)
        }
    }
    LaunchedEffect(currentRoute) {
        showBottomNavBar = AppDestinations.entries.map {it.route}.contains(currentRoute)
    }
    NavigationSuiteScaffold(
        layoutType = if (! showBottomNavBar) NavigationSuiteType.None else NavigationSuiteType.NavigationBar,
        navigationSuiteItems = {
            AppDestinations.entries.forEach {dest ->
                item(
                    icon = {Icon(dest.icon, contentDescription = dest.label)},
                    label = {Text(dest.label)},
                    selected = currentRoute == dest.route,
                    onClick = {
                        selectedDestination = dest
                        if (currentRoute != dest.route) {
                            navController.navigate(dest.route) {
                                popUpTo(HOME_ROUTE) {saveState = true}
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) {
        MainScreen(Modifier.fillMaxSize(), navController, viewModel)
    }
}