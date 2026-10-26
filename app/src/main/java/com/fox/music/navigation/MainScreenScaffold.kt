package com.fox.music.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.core.ui.theme.foxTabPageBackground
import com.fox.music.feature.stream.StreamRoutes
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
            .foxTabPageBackground(),
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
    sharedTransitionScope: SharedTransitionScope,
    // 「我的」页头部有自绘渐变、音流页要用沉浸式封面：这两处需要内容铺到状态栏下方，
    // 否则状态栏区域露出的是页面底色，与下方内容形成色差/断层。
    applyStatusBarsPadding: Boolean = true,
    // 覆盖层：绘制在**底部 Tab 栏之后**，保证（如音流页「听歌模式」侧边栏）能盖住底栏。
    overlayContent: @Composable () -> Unit = {},
    navScreen: @Composable AnimatedContentScope.(Modifier) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .foxTabPageBackground(),
    ) {
        // 片段模式只在音流页生效：切到其它底部 Tab 立即放开「只播高潮」限制，
        // 当前与后续歌曲按整曲播放；回到音流页重新开启（不会造成瞬间跳歌，见 MusicControllerImpl）。
        LaunchedEffect(tabRoute) {
            musicController.setStreamClipEnabled(tabRoute == StreamRoutes.STREAM)
        }

        navScreen(
            if (applyStatusBarsPadding) {
                Modifier.fillMaxSize().statusBarsPadding()
            } else {
                Modifier.fillMaxSize()
            }
        )

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
                isStreamPlaying = playerState.isPlaying,
                isStreamBuffering = playerState.isLoading,
                onStreamPlayPause = { musicController.togglePlay() },
            )
        }

        // 最后绘制：覆盖在底栏之上（音流页「听歌模式」侧边栏走这里）
        overlayContent()
    }
}
