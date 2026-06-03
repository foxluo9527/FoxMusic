package com.fox.music.ui

import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE

/** 底部 Tab 路由：仅这些页面内嵌导航栏，以参与 NavHost 转场与可预测返回动画。 */
val TAB_ROOT_ROUTES: Set<String> = AppDestinations.entries.map { it.route }.toSet()

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
fun AppBottomNavigationBar(
    navController: NavHostController,
    selectedRoute: String,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        AppDestinations.entries.forEach { dest ->
            val selected = selectedRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selectedRoute != dest.route) {
                        navController.navigate(dest.route) {
                            popUpTo(HOME_ROUTE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label,
                        modifier = Modifier
                            .size(if (selected) 26.dp else 24.dp)
                            .scale(if (selected) 1.08f else 1f),
                    )
                },
                label = {
                    Text(
                        text = dest.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
