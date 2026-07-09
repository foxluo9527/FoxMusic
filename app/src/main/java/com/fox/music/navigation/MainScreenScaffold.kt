package com.fox.music.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.ui.AppBottomNavigationBar

@Composable
fun AnimatedContentScope.MainScreenWithBottomBar(
    navController: NavHostController,
    musicController: MusicController,
    playerState: PlayerState,
    showMiniPlayer: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    navScreen: @Composable AnimatedContentScope.(Modifier) -> Unit,
) {
    Column {
        navScreen(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        if (showMiniPlayer) {
            MiniPlayer(
                playerState = playerState,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this@MainScreenWithBottomBar,
                onPlayPauseClick = { musicController.togglePlay() },
                onNextClick = { musicController.next() },
                onClick = { navController.navigate(PLAYER_ROUTE) }
            )
        }
    }
}

@Composable
fun AnimatedContentScope.MainScreenWithTabBar(
    navController: NavHostController,
    musicController: MusicController,
    playerState: PlayerState,
    tabRoute: String,
    showMiniPlayer: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    navScreen: @Composable AnimatedContentScope.(Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        navScreen(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .statusBarsPadding()
        )
        if (showMiniPlayer) {
            MiniPlayer(
                playerState = playerState,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this@MainScreenWithTabBar,
                onPlayPauseClick = { musicController.togglePlay() },
                onNextClick = { musicController.next() },
                onClick = { navController.navigate(PLAYER_ROUTE) }
            )
        }
        AppBottomNavigationBar(
            navController = navController,
            selectedRoute = tabRoute,
        )
    }
}
