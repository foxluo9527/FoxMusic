package com.fox.music

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fox.music.core.model.user.DarkMode
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.ui.MainScreen
import com.fox.music.ui.theme.FoxMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startRoute by lazy {
        mutableStateOf(HOME_ROUTE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("start_route")?.ifEmpty { null }?.let {
            startRoute.value = it
        }
        enableEdgeToEdge()
        setContent {
            FoxMusicApp(startRoute)
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        intent.getStringExtra("start_route")?.ifEmpty { null }?.let {
            startRoute.value = it
        }
    }
}

private val NavActiveColor = Color(0xFF5B9BD5)
private val NavInactiveColor = Color(0xFF999999)

enum class AppDestinations(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HOME_ROUTE, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER(DISCOVER_ROUTE, "Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    PROFILE(PROFILE_ROUTE, "Profile", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}

@Composable
fun FoxMusicApp(startRoute: MutableState<String>) {
    val viewModel: MainActivityViewModel = hiltViewModel()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val darkTheme = when (userPreferences.darkMode) {
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showBottomNavBar by remember { mutableStateOf(true) }
    LaunchedEffect(startRoute.value) {
//        navController.navigate(startRoute.value)
    }
    LaunchedEffect(currentRoute) {
        showBottomNavBar = AppDestinations.entries.map { it.route }.contains(currentRoute)
    }
    FoxMusicTheme(darkTheme = darkTheme) {
    NavigationSuiteScaffold(
        layoutType = if (!showBottomNavBar) NavigationSuiteType.None else NavigationSuiteType.NavigationBar,
        containerColor = Color.White,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = Color.White,
            navigationBarContentColor = NavInactiveColor,
        ),
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                val selected = currentRoute == dest.route
                item(
                    icon = {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            modifier = Modifier
                                .size(if (selected) 26.dp else 24.dp)
                                .scale(if (selected) 1.08f else 1f),
                            tint = if (selected) NavActiveColor else NavInactiveColor,
                        )
                    },
                    label = {
                        Text(
                            text = dest.label,
                            color = if (selected) NavActiveColor else NavInactiveColor,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    selected = selected,
                    onClick = {
                        selectedDestination = dest
                        if (currentRoute != dest.route) {
                            navController.navigate(dest.route) {
                                popUpTo(HOME_ROUTE) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) {
        MainScreen(Modifier.fillMaxSize(), navController, viewModel)
    }
    }
}
