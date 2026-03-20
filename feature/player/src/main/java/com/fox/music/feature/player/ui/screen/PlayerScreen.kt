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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.ImageWithPaletteColors
import com.fox.music.core.ui.component.TabSwitch
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import com.fox.music.feature.player.ui.component.LyricPage
import com.fox.music.feature.player.ui.component.LyricSettings
import com.fox.music.feature.player.ui.component.PlaylistBottomSheet
import com.fox.music.feature.player.ui.component.SongPage
import com.fox.music.feature.player.ui.component.MusicCommentBottomSheet
import kotlinx.coroutines.launch

const val PLAYER_ROUTE = "player"

// 判断颜色是否为浅色（用于状态栏图标颜色判断）
private fun isColorLight(color: Int): Boolean {
    val red = android.graphics.Color.red(color)
    val green = android.graphics.Color.green(color)
    val blue = android.graphics.Color.blue(color)
    // 使用相对亮度公式计算亮度
    val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
    // 如果亮度大于 0.5，认为是浅色
    return luminance > 0.5
}

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
    var dominantColor by remember {mutableStateOf(Color(0xFFF6F7F9))}
    var contrastColor by remember {mutableStateOf(Color(0xFF202122))}
    val currentStyle by LyricStyleManager.getInstance().styleFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState {2}
    var showLyricSettings by remember {mutableStateOf(false)}
    var showPlaylistSheet by remember {mutableStateOf(false)}
    var showCommentSheet by remember {mutableStateOf(false)}

    // 获取当前 Window 以控制状态栏
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    // 根据 contrastColor 动态设置状态栏图标颜色
    SideEffect {
        window?.let {
            val insetsController = WindowCompat.getInsetsController(it, view)
            val isLight = isColorLight(contrastColor.toArgb())
            insetsController.isAppearanceLightStatusBars = ! isLight
        }
    }

    // 退出时恢复默认的状态栏样式
    DisposableEffect(Unit) {
        onDispose {
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                // 恢复为浅色背景（深色图标）
                insetsController.isAppearanceLightStatusBars = true
            }
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFFF6F7F9))
        ) {
            Box(Modifier.fillMaxSize()) {
                ImageWithPaletteColors(
                    Modifier.fillMaxSize(),
                    playerState.currentMusic?.coverImage
                ) {dominant, contrast ->
                    dominantColor = Color(dominant)
                    contrastColor = Color(contrast)
                }
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 60.dp, bottom = 24.dp)
            ) {
                HorizontalPager(
                    pager,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
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
                            contrastColor,
                            onCommentClick = { showCommentSheet = true }
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onBack,
                        Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.Default.ArrowBackIosNew, contentDescription = "Back",
                            modifier = Modifier.size(24.dp).rotate(-90f),
                            tint = contrastColor
                        )
                    }
                    TabSwitch(Modifier.fillMaxWidth(), 2, pager.currentPage, contrastColor)
                    if (pager.currentPage == 1) {
                        IconButton(onClick = {
                            showLyricSettings = ! showLyricSettings
                        }, Modifier.align(Alignment.CenterEnd)) {
                            Icon(
                                Icons.Default.Settings, contentDescription = "Lyric Settings",
                                modifier = Modifier.size(24.dp),
                                tint = contrastColor
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            showPlaylistSheet = true
                        }, Modifier.align(Alignment.CenterEnd)) {
                            Icon(
                                Icons.Default.QueueMusic, contentDescription = "Playlist",
                                modifier = Modifier.size(24.dp),
                                tint = contrastColor
                            )
                        }
                    }
                }
                if (showLyricSettings && pager.currentPage == 1) {
                    LyricSettings(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.3f))
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        // 播放列表底部工作表
        if (showPlaylistSheet) {
            PlaylistBottomSheet(
                playerState = playerState,
                musicController = musicController,
                onDismiss = {
                    showPlaylistSheet = false
                    onBack()
                }
            )
        }

        // 评论底部弹窗
        if (showCommentSheet) {
            MusicCommentBottomSheet(
                musicId = playerState.currentMusic?.id ?: 0L,
                onDismiss = {
                    showCommentSheet = false
                }
            )
        }
    }
}
