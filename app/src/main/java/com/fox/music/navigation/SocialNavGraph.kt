package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.profile.ui.screen.userHomeRoute
import com.fox.music.feature.social.ui.screen.CREATE_POST_ROUTE
import com.fox.music.feature.social.POST_DETAIL_ROUTE
import com.fox.music.feature.social.ui.screen.SELECT_POST_MUSIC_ROUTE
import com.fox.music.feature.social.ui.screen.SELECT_POST_MUSIC_SEARCH_ROUTE
import com.fox.music.feature.social.ui.screen.SELECT_POST_TAG_ROUTE
import com.fox.music.feature.social.ui.screen.CreatePostScreen
import com.fox.music.feature.social.ui.screen.PostPublishQueueScreen
import com.fox.music.feature.social.ui.screen.POST_PUBLISH_QUEUE_ROUTE
import com.fox.music.feature.social.ui.screen.PostDetailScreen
import com.fox.music.feature.social.ui.screen.SelectPostMusicScreen
import com.fox.music.feature.social.ui.screen.SelectPostMusicSearchScreen
import com.fox.music.feature.social.ui.screen.SelectPostTagScreen
import com.fox.music.feature.search.SearchResultTab
import com.fox.music.feature.search.searchRoute
import com.fox.music.feature.social.ui.screen.createPostRoute
import com.fox.music.feature.social.viewmodel.PostDetailViewModel
import com.fox.music.core.model.music.PlaybackPlaylistSource

fun NavGraphBuilder.socialNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController
    val musicController = context.musicController

    fun navigateToCreatePost(postId: Long = -1L) {
        if (!context.authState.isLoggedIn) {
            navController.navigate(LOGIN_ROUTE)
            return
        }
        navController.navigate(createPostRoute(postId))
    }

    composableStack(
        route = POST_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("postId") { type = NavType.LongType },
        ),
    ) {
        val animatedScope = this
        with(sharedTransitionScope) {
            PostDetailScreen(
                modifier = Modifier.fillMaxSize(),
                currentUserId = context.currentUserId,
                isLoggedIn = context.authState.isLoggedIn,
                playerState = context.playerState,
                sharedTransitionScope = this@with,
                animatedContentScope = animatedScope,
                onBack = { navController.popBackStack() },
                onLoginRequired = { navController.navigate(LOGIN_ROUTE) },
                onSharePost = context.postActionsViewModel::sharePost,
                onEditPost = { postId -> navigateToCreatePost(postId) },
                onReportPost = context.postActionsViewModel::showReportSheet,
                onDeletePost = context.postActionsViewModel::showDeleteDialog,
                onCommentDeleted = { context.onPlazaRefreshTriggerIncrement() },
                onPlayMusic = { post ->
                    post.music?.let { music ->
                        context.musicController.setPlaylist(
                            listOf(music),
                            0,
                            "post_${post.id}",
                            PlaybackPlaylistSource.fromPost(post),
                        )
                    }
                },
                onNavigateToPlayer = { navController.navigateToPlayer() },
                onTogglePlay = { musicController.togglePlay() },
                onNextTrack = { musicController.next() },
                onTagClick = { tag ->
                    navController.navigate(
                        searchRoute(
                            tab = SearchResultTab.POST,
                            tag = tag,
                            sort = "hot",
                        ),
                    )
                },
                onAddMusicToPlaylist = { music ->
                    context.musicActionsViewModel.showAddToPlaylist(listOf(music))
                },
                onNavigateToUserHome = { userId ->
                    navController.navigate(userHomeRoute(userId))
                },
            )
        }
    }

    composableStack(POST_PUBLISH_QUEUE_ROUTE) {
        PostPublishQueueScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(
        route = CREATE_POST_ROUTE,
        arguments = listOf(
            navArgument("postId") {
                type = NavType.LongType
                defaultValue = -1L
            },
        ),
    ) {
        CreatePostScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onSelectTag = { navController.navigate(SELECT_POST_TAG_ROUTE) },
            onSelectMusic = { navController.navigate(SELECT_POST_MUSIC_ROUTE) },
            onPublished = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(PostDetailViewModel.POST_UPDATED_KEY, true)
                context.onPlazaRefreshTriggerIncrement()
                navController.popBackStack()
            },
        )
    }

    composableStack(SELECT_POST_TAG_ROUTE) {
        SelectPostTagScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onTagSelected = { navController.popBackStack() },
        )
    }

    composableStack(SELECT_POST_MUSIC_ROUTE) {
        SelectPostMusicScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onSearchClick = { navController.navigate(SELECT_POST_MUSIC_SEARCH_ROUTE) },
            onMusicSelected = { navController.popBackStack() },
        )
    }

    composableStack(SELECT_POST_MUSIC_SEARCH_ROUTE) {
        SelectPostMusicSearchScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onMusicSelected = {
                navController.popBackStack(SELECT_POST_MUSIC_ROUTE, inclusive = true)
            },
        )
    }
}
