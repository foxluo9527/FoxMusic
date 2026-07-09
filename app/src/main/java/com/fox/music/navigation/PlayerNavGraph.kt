package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.feature.player.ui.screen.MUSIC_COMMENT_DETAIL_ROUTE
import com.fox.music.feature.player.ui.screen.MusicCommentDetailScreen
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.player.ui.screen.PlayerScreen

fun NavGraphBuilder.playerNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController

    composableStack(
        route = MUSIC_COMMENT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("musicId") { type = NavType.LongType },
            navArgument("commentId") { type = NavType.LongType },
        ),
    ) {
        MusicCommentDetailScreen(
            modifier = Modifier.fillMaxSize(),
            musicId = it.arguments?.getLong("musicId") ?: 0L,
            commentId = it.arguments?.getLong("commentId") ?: 0L,
            navController = navController,
        )
    }

    composablePlayer(PLAYER_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            PlayerScreen(
                musicController = context.musicController,
                sharedTransitionScope = this@with,
                animatedContentScope = animatedScope,
                onBack = { context.navController.popBackStack() }
            )
        }
    }
}
