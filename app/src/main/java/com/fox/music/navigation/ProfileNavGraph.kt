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
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.auth.ui.screen.LoginScreen
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.fox.music.feature.player.ui.screen.MANAGE_ROUTER
import com.fox.music.feature.player.ui.screen.ManageScreen
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.profile.ui.screen.DESKTOP_LYRIC_SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.DESKTOP_WIDGET_ROUTE
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.profile.ui.screen.DesktopLyricSettingsScreen
import com.fox.music.feature.profile.ui.screen.DesktopWidgetSettingsScreen
import com.fox.music.feature.profile.ui.screen.WidgetPinOption
import com.fox.music.feature.profile.ui.screen.EDIT_PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.EditProfileScreen
import com.fox.music.feature.profile.ui.screen.LOCK_SCREEN_LYRIC_ROUTE
import com.fox.music.feature.profile.ui.screen.LockScreenLyricSettingsScreen
import com.fox.music.feature.profile.ui.screen.REPORT_HISTORY_ROUTE
import com.fox.music.feature.profile.ui.screen.ReportHistoryScreen
import com.fox.music.feature.profile.ui.screen.SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.SettingsScreen
import com.fox.music.feature.profile.ui.screen.THEME_SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.ThemeSettingsScreen
import com.fox.music.feature.profile.ui.screen.DownloadScreen
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
                    onNavigateToPlayer = { navController.navigate(PLAYER_ROUTE) },
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

    composableStack(EDIT_PROFILE_ROUTE) {
        EditProfileScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onProfileSaved = context.onProfileRefreshKeyIncrement,
        )
    }

    composableStack(MANAGE_ROUTER) {
        ManageScreen(Modifier.fillMaxSize()) {
            navController.popBackStack()
        }
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
