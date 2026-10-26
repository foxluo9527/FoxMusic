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
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.core.model.music.PlaybackSourceKind
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.search.searchRoute
import com.fox.music.feature.social.postDetailRoute
import com.fox.music.feature.stream.StreamRoutes

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
                onBack = { context.navController.popBackStack() },
                onPlaylistSourceClick = { source ->
                    when (source.kind) {
                        PlaybackSourceKind.POST -> {
                            val id = source.id ?: return@PlayerScreen
                            navController.navigateFromPlayerToDetail(postDetailRoute(id))
                        }
                        PlaybackSourceKind.SEARCH -> {
                            // 搜索来源没有实体 id，用来源标题（即搜索关键词）重建搜索页并自动执行
                            val query = source.title ?: return@PlayerScreen
                            navController.navigate(searchRoute(query = query))
                        }
                        PlaybackSourceKind.DOWNLOAD -> {
                            navController.navigate(DOWNLOAD_MANAGER_ROUTE)
                        }
                        PlaybackSourceKind.STREAM -> {
                            // 先回到主壳再切音流 Tab，不能把音流压在播放页回退栈上。
                            navController.navigateFromPlayerToTab(StreamRoutes.STREAM)
                        }
                        else -> {
                            val detailType = source.toDetailType() ?: return@PlayerScreen
                            val id = source.id ?: return@PlayerScreen
                            navController.navigateFromPlayerToDetail(
                                playlistDetailRoute(id, detailType),
                            )
                        }
                    }
                },
            )
        }
    }
}
