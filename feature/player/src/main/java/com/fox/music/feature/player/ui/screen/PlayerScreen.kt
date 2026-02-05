package com.fox.music.feature.player.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.ImageWithPaletteColors
import com.fox.music.core.ui.component.TabSwitch
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import kotlinx.coroutines.launch

const val PLAYER_ROUTE = "player"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    musicController: MusicController,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onBack: () -> Unit = {},
    onFavoriteClick: (Long?) -> Unit = {},
) {
    val playerState by musicController.playerState.collectAsState(PlayerState())
    var dominantColor by remember {mutableStateOf(Color.White)}
    var contrastColor by remember {mutableStateOf(Color.Black)}
    val currentStyle by LyricStyleManager.getInstance().styleFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState {2}
    var showLyricSettings by remember {mutableStateOf(false)}
    with(sharedTransitionScope) {
        Box(
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFFF6F7F9))
        ) {
            Box(Modifier.fillMaxSize()){
                ImageWithPaletteColors(
                    Modifier.fillMaxSize(),
                    playerState.currentMusic?.coverImage
                ) {dominant, contrast ->
                    dominantColor = Color(dominant)
                    contrastColor = Color(contrast)
                }
                Spacer(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
            }
            Column(
                Modifier
                    .padding()
                    .fillMaxSize()
                    .padding(top = 60.dp,bottom = 24.dp)
            ) {
                HorizontalPager(pager, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    if (it == 0) {
                        SongPage(
                            Modifier.fillMaxSize(),
                            playerState,
                            musicController,
                            contrastColor,
                            animatedContentScope,
                            onFavoriteClick
                        ) {
                            scope.launch {
                                pager.animateScrollToPage(1)
                            }
                        }
                    } else {
                        LyricPage(
                            Modifier.fillMaxSize(),
                            currentStyle,
                            null,
                            musicController,
                            dominantColor,
                            contrastColor
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBack, Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.Default.ArrowBackIosNew, contentDescription = "Back",
                            modifier = Modifier.size(30.dp),
                            tint = contrastColor
                        )
                    }
                    TabSwitch(Modifier.fillMaxWidth(), 2, pager.currentPage, contrastColor)
                    if (pager.currentPage == 1) {
                        IconButton(onClick = {
                            showLyricSettings = !showLyricSettings
                        }, Modifier.align(Alignment.CenterEnd)) {
                            Icon(
                                Icons.Default.Settings, contentDescription = "Back",
                                modifier = Modifier.size(30.dp),
                                tint = contrastColor
                            )
                        }
                    }
                }
                if (showLyricSettings && pager.currentPage == 1) {
                    LyricSettings(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.3f)).padding(vertical = 8.dp))
                }
            }
        }
    }
}

