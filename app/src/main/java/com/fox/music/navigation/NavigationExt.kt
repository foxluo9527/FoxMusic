package com.fox.music.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.stream.StreamRoutes
import com.fox.music.ui.TAB_ROOT_ROUTES

/**
 * 带底栏的主壳。音流是 Tab，但中央槽位不在 [TAB_ROOT_ROUTES] 里。
 * 播放页及其上的详情都不算主壳。
 */
private val MAIN_SHELL_ROUTES: Set<String> = TAB_ROOT_ROUTES + StreamRoutes.STREAM

fun NavController.navigateToPlayer() {
    navigate(PLAYER_ROUTE) {
        launchSingleTop = true
    }
}

fun NavController.navigateToDetail(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * 从播放页回到来源详情：若目标详情页就在回退栈下一层，直接 pop，避免无限套娃。
 */
fun NavController.navigateFromPlayerToDetail(route: String) {
    if (previousBackStackEntry?.matchesResolvedRoute(route) == true) {
        popBackStack()
    } else {
        navigateToDetail(route)
    }
}

fun NavOptionsBuilder.singleTopReuse() {
    launchSingleTop = true
}

/**
 * 底栏切 Tab。播放页跳音流、底栏、助手里的 Tab 跳转都走这里，避免选项分叉。
 * popUpTo 首页并 saveState / restoreState，每个 Tab 保留自己的回退栈。
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(HOME_ROUTE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 播放列表来源是底栏 Tab（音流）时：先回到当前主壳，再按底栏同款选项切 Tab。
 *
 * 直接 navigate(Tab) 会把 Tab 压在播放页上。之后点「首页」时，
 * popUpTo(首页){saveState} 把 [播放页, Tab] 记到首页 id，restoreState 又整段恢复，
 * 首页看起来没有切换，系统返回回到播放页。
 * 这里弹出播放页时 saveState = false，避免这段栈被记进首页。
 */
fun NavController.navigateFromPlayerToTab(route: String) {
    val shellRoute = currentBackStack.value
        .lastOrNull { it.destination.route in MAIN_SHELL_ROUTES }
        ?.destination
        ?.route
    if (shellRoute != null && currentDestination?.route != shellRoute) {
        popBackStack(shellRoute, inclusive = false, saveState = false)
    }
    navigateToTab(route)
}

private fun NavBackStackEntry.matchesResolvedRoute(targetRoute: String): Boolean {
    return resolveRoute() == targetRoute
}

private fun NavBackStackEntry.resolveRoute(): String? {
    val pattern = destination.route ?: return null
    return when (pattern) {
        "post_detail/{postId}" -> {
            val postId = arguments?.getLong("postId") ?: return null
            "post_detail/$postId"
        }
        "playlist_detail/{playlistId}/{type}" -> {
            val playlistId = arguments?.getLong("playlistId") ?: return null
            val type = arguments?.getString("type") ?: return null
            "playlist_detail/$playlistId/$type"
        }
        else -> pattern
    }
}
