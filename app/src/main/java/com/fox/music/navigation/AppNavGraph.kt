package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder

fun NavGraphBuilder.appNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    tabNavGraph(context, sharedTransitionScope)
    musicNavGraph(context, sharedTransitionScope)
    socialNavGraph(context)
    chatNavGraph(context)
    profileNavGraph(context, sharedTransitionScope)
    playerNavGraph(context, sharedTransitionScope)
}
