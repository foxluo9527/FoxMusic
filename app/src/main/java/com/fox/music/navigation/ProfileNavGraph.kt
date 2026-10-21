package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.auth.ui.screen.LoginScreen
import com.fox.music.feature.chat.NOTIFICATION_SETTINGS_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.fox.music.feature.player.ui.screen.MANAGE_ROUTER
import com.fox.music.feature.player.ui.screen.MANAGE_SEARCH_ROUTE
import com.fox.music.feature.player.ui.screen.manageSearchRoute
import com.fox.music.feature.player.ui.screen.ManageScreen
import com.fox.music.feature.player.ui.screen.ManageSearchScreen
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.player.ui.screen.SOURCE_CORRECT_ROUTE
import com.fox.music.feature.player.ui.screen.SourceCorrectScreen
import com.fox.music.feature.player.ui.screen.CollectTrackScreen
import com.fox.music.feature.player.viewmodel.COLLECT_TRACK_ROUTE
import com.fox.music.feature.player.viewmodel.collectTrackArgsFrom
import com.fox.music.feature.player.viewmodel.collectTrackRoute
import com.fox.music.feature.profile.ui.screen.DESKTOP_LYRIC_SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.DESKTOP_WIDGET_ROUTE
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.profile.ui.screen.DesktopLyricSettingsScreen
import com.fox.music.feature.profile.ui.screen.DesktopWidgetSettingsScreen
import com.fox.music.feature.profile.ui.screen.DownloadScreen
import com.fox.music.feature.profile.ui.screen.WidgetPinOption
import com.fox.music.feature.profile.ui.screen.EDIT_PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.EditProfileScreen
import com.fox.music.feature.profile.ui.screen.LOCK_SCREEN_LYRIC_ROUTE
import com.fox.music.feature.profile.ui.screen.LockScreenLyricSettingsScreen
import com.fox.music.feature.profile.ui.screen.REPORT_HISTORY_ROUTE
import com.fox.music.feature.profile.ui.screen.ReportHistoryScreen
import com.fox.music.feature.profile.ui.screen.FEEDBACK_HISTORY_ROUTE
import com.fox.music.feature.profile.ui.screen.FeedbackHistoryScreen
import com.fox.music.feature.profile.ui.screen.SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.SettingsScreen
import com.fox.music.feature.profile.ui.screen.THEME_SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.ThemeSettingsScreen
import com.fox.music.feature.profile.ui.screen.PRIVACY_SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.PrivacySettingsScreen
import com.fox.music.feature.profile.ui.screen.USER_HOME_ROUTE
import com.fox.music.feature.profile.ui.screen.UserHomeScreen
import com.fox.music.navigation.navigateToPlayer
import com.fox.music.realtime.BackgroundPermissionGuide
import com.fox.music.ui.BackgroundPermissionGuideDialog
import com.fox.music.car.CarModeActivity
import com.fox.music.lyric.LockScreenLyricActivity
import com.fox.music.widget.FoxMusicWidgetReceiver
import com.fox.music.widget.FoxMusicWidgetLargeReceiver
import com.fox.music.widget.FoxMusicWidgetSmallReceiver

fun NavGraphBuilder.profileNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController
    val musicController = context.musicController
    val playerState = context.playerState
    val showBottomBar = context.showBottomBar

    composableStack(SETTINGS_ROUTE) {
        SettingsScreen(
            modifier = Modifier.fillMaxSize(),
            isLogin = context.authState.isLoggedIn,
            onDesktopLyricSettings = {
                navController.navigate(DESKTOP_LYRIC_SETTINGS_ROUTE)
            },
            onThemeSettings = {
                navController.navigate(THEME_SETTINGS_ROUTE)
            },
            onBack = { navController.popBackStack() },
            onLogin = { navController.navigate(LOGIN_ROUTE) },
            onEditProfile = { navController.navigate(EDIT_PROFILE_ROUTE) },
            onManageLibrary = { navController.navigate(MANAGE_ROUTER) },
            onDownloadManager = { navController.navigate(DOWNLOAD_MANAGER_ROUTE) },
            onReportHistory = { navController.navigate(REPORT_HISTORY_ROUTE) },
            onFeedbackHistory = { navController.navigate(FEEDBACK_HISTORY_ROUTE) },
            onPrivacySettings = { navController.navigate(PRIVACY_SETTINGS_ROUTE) },
            // 通知设置页（feature:chat 的 NotificationSettingsScreen）原本只挂在
            // MessagesContent 的 showBackButton=true 分支上，而两端都传 false →
            // 页面在两端都进不去。这里从设置页给一个可达入口。
            onNotificationSettings = { navController.navigate(NOTIFICATION_SETTINGS_ROUTE) },
            onInstallApk = context.viewModel::requestInstall,
            onLogout = {
                navController.navigate(LOGIN_ROUTE) {
                    popUpTo(HOME_ROUTE) { inclusive = false }
                }
            },
        )
    }

    composableStack(THEME_SETTINGS_ROUTE) {
        ThemeSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(DESKTOP_LYRIC_SETTINGS_ROUTE) {
        val screenContext = LocalContext.current
        val prefs by context.viewModel.userPreferences.collectAsState()
        LaunchedEffect(prefs.desktopLyricEnabled) {
            LyricSyncManager.getInstance().syncDesktopLyricEnabled(prefs.desktopLyricEnabled)
        }
        DesktopLyricSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onLockScreenLyric = { navController.navigate(LOCK_SCREEN_LYRIC_ROUTE) },
            onDesktopWidget = { navController.navigate(DESKTOP_WIDGET_ROUTE) },
            onCarMode = {
                screenContext.startActivity(CarModeActivity.intent(screenContext))
            },
            onRequestOverlayPermission = { onGranted ->
                LyricSyncManager.getInstance().openDesktopLyric(screenContext, onGranted)
            },
        )
    }

    composableStack(LOCK_SCREEN_LYRIC_ROUTE) {
        val screenContext = LocalContext.current
        LockScreenLyricSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            musicController = musicController,
            onBack = { navController.popBackStack() },
            onPreviewLockScreenLyric = {
                screenContext.startActivity(LockScreenLyricActivity.intent(screenContext))
            },
        )
    }

    composableStack(DESKTOP_WIDGET_ROUTE) {
        DesktopWidgetSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            musicController = musicController,
            widgetPinOptions = listOf(
                WidgetPinOption("迷你小组件", FoxMusicWidgetSmallReceiver::class.java.name),
                WidgetPinOption("标准小组件", FoxMusicWidgetReceiver::class.java.name),
                WidgetPinOption("大卡小组件", FoxMusicWidgetLargeReceiver::class.java.name),
            ),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(DOWNLOAD_MANAGER_ROUTE) {
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                DownloadScreen(
                    modifier = it,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = { navController.navigateToPlayer() },
                )
            }
        }
    }

    composableStack(REPORT_HISTORY_ROUTE) {
        ReportHistoryScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(FEEDBACK_HISTORY_ROUTE) {
        FeedbackHistoryScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(PRIVACY_SETTINGS_ROUTE) {
        PrivacySettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(
        route = USER_HOME_ROUTE,
        arguments = listOf(navArgument("userId") { type = NavType.LongType }),
    ) {
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                UserHomeScreen(
                    modifier = it,
                    currentUserId = context.currentUserId,
                    onBack = { navController.popBackStack() },
                    onNavigateToEditProfile = { navController.navigate(EDIT_PROFILE_ROUTE) },
                    onNavigateToPrivacySettings = { navController.navigate(PRIVACY_SETTINGS_ROUTE) },
                    onNavigateToChat = { userId, nickname, avatar ->
                        navController.navigate(
                            com.fox.music.feature.chat.chatDetailRoute(userId, nickname, avatar),
                        )
                    },
                    onNavigateToAddFriend = { userId, nickname ->
                        navController.navigate(
                            com.fox.music.feature.chat.addFriendRoute(userId, nickname),
                        )
                    },
                    onNavigateToPlaylist = { id ->
                        navController.navigate(
                            com.fox.music.feature.playlist.ui.component.playlistDetailRoute(id),
                        )
                    },
                    onNavigateToPostDetail = { postId ->
                        navController.navigateToDetail(
                            com.fox.music.feature.social.postDetailRoute(postId),
                        )
                    },
                    onSharePost = context.postActionsViewModel::sharePost,
                    onShareProfile = { profile ->
                        context.profileShareActionsViewModel.shareProfile(
                            userId = profile.id,
                            nickname = profile.nickname,
                            signature = profile.signature,
                        )
                    },
                    onReportPost = context.postActionsViewModel::showReportSheet,
                    onDeletePost = context.postActionsViewModel::showDeleteDialog,
                    onEditPost = { postId ->
                        navController.navigate(
                            com.fox.music.feature.social.ui.screen.createPostRoute(postId),
                        )
                    },
                    onNavigateToPlayer = { navController.navigateToPlayer() },
                )
            }
        }
    }

    composableStack(EDIT_PROFILE_ROUTE) {
        EditProfileScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onProfileSaved = context.onProfileRefreshKeyIncrement,
        )
    }

    composableStack(MANAGE_ROUTER) {
        ManageScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onAddMusic = { navController.navigate(manageSearchRoute()) },
            onLogin = { navController.navigate(LOGIN_ROUTE) },
        )
    }

    composableStack(
        route = MANAGE_SEARCH_ROUTE,
        arguments = listOf(navArgument("query") { type = NavType.StringType }),
    ) { backStackEntry ->
        ManageSearchScreen(
            modifier = Modifier.fillMaxSize(),
            initialQuery = backStackEntry.arguments?.getString("query").orEmpty(),
            onBack = { navController.popBackStack() },
            onLogin = { navController.navigate(LOGIN_ROUTE) },
            onNavigateToCollect = { music ->
                navController.navigate(
                    collectTrackRoute(
                        com.fox.music.feature.player.viewmodel.CollectTrackArgs(
                            title = music.title,
                            artist = music.artists.joinToString(", ") { it.name },
                            platform = music.sourcePlatform ?: "netease",
                            externalId = music.sourceId,
                            coverUrl = music.coverImage.orEmpty(),
                            durationMs = music.duration,
                            album = music.album?.title ?: "",
                            mode = "add_single",
                        )
                    )
                )
            },
        )
    }

    composableStack(
        route = COLLECT_TRACK_ROUTE,
        arguments = listOf(
            navArgument("title") { type = NavType.StringType },
            navArgument("artist") { type = NavType.StringType },
            navArgument("platform") { type = NavType.StringType },
            navArgument("externalId") { type = NavType.StringType },
            navArgument("coverUrl") { type = NavType.StringType },
            navArgument("durationMs") { type = NavType.StringType },
            navArgument("album") { type = NavType.StringType },
            navArgument("mode") { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        CollectTrackScreen(
            args = collectTrackArgsFrom(backStackEntry.savedStateHandle),
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(
        route = SOURCE_CORRECT_ROUTE,
        arguments = listOf(navArgument("musicId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val musicId = backStackEntry.arguments?.getLong("musicId") ?: 0L
        SourceCorrectScreen(
            modifier = Modifier.fillMaxSize(),
            musicId = musicId,
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(LOGIN_ROUTE) {
        val loginContext = LocalContext.current
        var showBackgroundGuide by remember { mutableStateOf(false) }
        val navigateHomeFromLogin = {
            navController.navigate(HOME_ROUTE) {
                popUpTo(HOME_ROUTE) { inclusive = false }
                launchSingleTop = true
            }
        }
        if (showBackgroundGuide) {
            BackgroundPermissionGuideDialog(
                onLater = {
                    showBackgroundGuide = false
                    navigateHomeFromLogin()
                },
                onConfirmed = {
                    showBackgroundGuide = false
                    navigateHomeFromLogin()
                },
            )
        }
        LoginScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToHome = {
                if (BackgroundPermissionGuide.shouldShowAfterLogin(loginContext)) {
                    showBackgroundGuide = true
                } else {
                    navigateHomeFromLogin()
                }
            },
        )
    }
}
