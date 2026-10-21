package com.fox.music.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.composableTab(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { tabEnter() },
        exitTransition = { tabExit() },
        popEnterTransition = { tabEnter() },
        popExitTransition = { tabExit() },
        content = content,
    )
}

fun NavGraphBuilder.composableStack(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { stackEnter() },
        exitTransition = { stackExit() },
        popEnterTransition = { stackPopEnter() },
        popExitTransition = { stackPopExit() },
        content = content,
    )
}

/** 从底部升起的二级页：进入上滑、返回下滑消失（不使用左右滑） */
fun NavGraphBuilder.composableSheetStack(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { sheetEnter() },
        exitTransition = { sheetExit() },
        popEnterTransition = { sheetPopEnter() },
        popExitTransition = { sheetPopExit() },
        content = content,
    )
}

fun NavGraphBuilder.composablePlayer(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { playerEnter() },
        exitTransition = {
            val toDetail = targetState.destination.route?.let(::isOverlayDetailRoute) == true
            if (toDetail) ExitTransition.None else playerExit()
        },
        popEnterTransition = {
            val fromDetail = initialState.destination.route?.let(::isOverlayDetailRoute) == true
            if (fromDetail) EnterTransition.None else playerEnter()
        },
        popExitTransition = { playerExit() },
        content = content,
    )
}
