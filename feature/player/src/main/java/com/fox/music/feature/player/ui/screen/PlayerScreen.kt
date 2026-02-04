package com.fox.music.feature.player.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    val scope = rememberCoroutineScope()
    with(sharedTransitionScope) {
        val pager = rememberPagerState {2}
        Box(
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color.White)
        ) {
            ImageWithPaletteColors(
                Modifier.fillMaxSize(),
                playerState.currentMusic?.coverImage
            ) {dominant, contrast ->
                dominantColor = Color(dominant)
                contrastColor = Color(contrast)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBack, Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.Default.Close, contentDescription = "Back",
                            modifier = Modifier.size(30.dp),
                            tint = contrastColor
                        )
                    }
                    TabSwitch(Modifier.fillMaxWidth(), 2, pager.currentPage, contrastColor)
                }
                HorizontalPager(pager, modifier = Modifier.weight(1f)) {
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
                            Modifier.fillMaxSize(), playerState, null, dominantColor, contrastColor
                        )
                    }
                }
            }
        }
    }
}

