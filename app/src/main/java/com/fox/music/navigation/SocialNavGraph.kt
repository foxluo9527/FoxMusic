package com.fox.music.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.feature.social.ui.screen.COMMUNITY_PLAZA_ROUTE
import com.fox.music.feature.social.ui.screen.CREATE_POST_ROUTE
import com.fox.music.feature.social.ui.screen.POST_DETAIL_ROUTE
import com.fox.music.feature.social.ui.screen.CommunityPlazaScreen
import com.fox.music.feature.social.ui.screen.CreatePostScreen
import com.fox.music.feature.social.ui.screen.PostDetailScreen
import com.fox.music.feature.social.ui.screen.createPostRoute
import com.fox.music.feature.social.ui.screen.postDetailRoute
import com.fox.music.feature.social.viewmodel.PostDetailViewModel

fun NavGraphBuilder.socialNavGraph(context: NavGraphContext) {
    val navController = context.navController

    composableStack(COMMUNITY_PLAZA_ROUTE) {
        CommunityPlazaScreen(
            modifier = Modifier.fillMaxSize(),
            refreshTrigger = context.plazaRefreshTrigger,
            currentUserId = context.currentUserId,
            onBack = { navController.popBackStack() },
            onCreatePost = { navController.navigate(createPostRoute()) },
            onPostDetail = { postId ->
                navController.navigate(postDetailRoute(postId))
            },
            onSharePost = context.postActionsViewModel::sharePost,
            onEditPost = { postId ->
                navController.navigate(createPostRoute(postId))
            },
            onReportPost = context.postActionsViewModel::showReportSheet,
            onDeletePost = context.postActionsViewModel::showDeleteDialog,
        )
    }

    composableStack(
        route = POST_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("postId") { type = NavType.LongType },
        ),
    ) {
        PostDetailScreen(
            modifier = Modifier.fillMaxSize(),
            currentUserId = context.currentUserId,
            onBack = { navController.popBackStack() },
            onSharePost = context.postActionsViewModel::sharePost,
            onEditPost = { postId ->
                navController.navigate(createPostRoute(postId))
            },
            onReportPost = context.postActionsViewModel::showReportSheet,
            onDeletePost = context.postActionsViewModel::showDeleteDialog,
            onCommentDeleted = { context.onPlazaRefreshTriggerIncrement() },
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
            onPublished = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(PostDetailViewModel.POST_UPDATED_KEY, true)
                context.onPlazaRefreshTriggerIncrement()
                navController.popBackStack()
            },
        )
    }
}
