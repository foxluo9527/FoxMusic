package com.fox.music.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE

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
