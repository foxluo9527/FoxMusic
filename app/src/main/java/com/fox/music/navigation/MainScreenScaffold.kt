package com.fox.music.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.MiniPlayer
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                navScreen(Modifier.fillMaxSize())
            }
            if (showMiniPlayer) {
                MiniPlayer(
                    playerState = playerState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this@MainScreenWithBottomBar,
                    onPlayPauseClick = { musicController.togglePlay() },
                    onNextClick = { musicController.next() },
                    onClick = { navController.navigateToPlayer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                )
            }
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
    messagesUnreadCount: Int = 0,
    sharedTransitionScope: SharedTransitionScope,
    navScreen: @Composable AnimatedContentScope.(Modifier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to scheme.primary.copy(alpha = 0.14f),
                            0.22f to scheme.background,
                            1f to scheme.background,
                        ),
                    ),
                ),
        ) {
            navScreen(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            if (showMiniPlayer) {
                MiniPlayer(
                    playerState = playerState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this@MainScreenWithTabBar,
                    onPlayPauseClick = { musicController.togglePlay() },
                    onNextClick = { musicController.next() },
                    onClick = { navController.navigateToPlayer() },
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            AppBottomNavigationBar(
                navController = navController,
                selectedRoute = tabRoute,
                messagesUnreadCount = messagesUnreadCount,
            )
        }
    }
}
