package com.fox.music.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.common.R as CommonR
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.ui.theme.AccentTerracottaBright
import com.fox.music.core.ui.theme.CoverShapeMini
import com.fox.music.core.ui.theme.FoxMusicTheme
import com.fox.music.core.ui.theme.InkGlass
import com.fox.music.core.ui.theme.MiniPlayerShape

/**
 * 与 [com.fox.music.feature.player.ui.screen.PlayerScreen] 的共享元素过渡需在 overlay 中渲染；
 * 设为 false 会断开 MiniPlayer ↔ 详情页的 hero 动画。
 */
private const val MINI_PLAYER_SHARED_IN_OVERLAY = true

/** 迷你条固定用浅色前景：底色是恒定的磨砂黑，不再跟随封面取色。 */
private val MiniPlayerInk = Color(0xFFFBF8F4)
private val MiniPlayerInkMuted = Color(0x8CFFFFFF)

/**
 * 迷你播放条（悬浮在底栏之上）。
 *
 * 2026-10 改版要点（原型 01 首页 .mini）：
 * - **去掉封面取色渐变**。原来那层「黑 → 黑 → 琥珀」横向渐变 + 取色反色文字，
 *   每换一首歌颜色都在跳，是典型的"AI 味"（用算法代替设计决策）。
 *   现在恒定磨砂黑 `#17140F`：重量低于内容、高于背景，且不和内容抢颜色。
 * - 高度 56dp、封面 38dp / 8dp 圆角、圆角 15dp：比原来矮一档，不再像"压在页面上的块"。
 * - 进度线 2dp 暖橙（`#E2563C`）贴在底边，替代原来通栏的琥珀细线。
 * - 所有 `sharedElement` key 保持不变，hero 动画不断。
 */
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val music = playerState.currentMusic ?: return
    val progress by remember(playerState.duration, playerState.position) {
        derivedStateOf {
            if (playerState.duration > 0) {
                (playerState.position.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
    val currentLyric by remember(music.id, music.lyrics, playerState.position) {
        derivedStateOf {
            music.getCurrentLyric(playerState.position)
                ?: music.artists.joinToString(", ") { it.name }
        }
    }
    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(MiniPlayerShape)
                .background(InkGlass)
                .clickable(onClick = onClick),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .size(38.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = "music-image-${music.id}",
                                ),
                                animatedVisibilityScope = animatedContentScope,
                                renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                            ),
                        shape = CoverShapeMini,
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = music.title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MiniPlayerInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = "music-title-${music.id}",
                                ),
                                animatedVisibilityScope = animatedContentScope,
                                renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                            ),
                        )
                        Text(
                            text = currentLyric,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MiniPlayerInkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = "music-lyric-${music.id}",
                                ),
                                animatedVisibilityScope = animatedContentScope,
                                renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                            ),
                        )
                    }
                    IconButton(
                        onClick = onPlayPauseClick,
                        // 不挂 sharedElement：每个 Tab 都有一份 MiniPlayer，同名 key 在
                        // Tab 互切时会匹配并做位置/尺寸插值 → 图标向右下漂移。
                        // hero 只保留封面/标题/歌词，这颗小按钮随各自迷你条淡入淡出即可。
                        modifier = Modifier.size(29.dp),
                    ) {
                        if (playerState.isLoading) {
                            CircularProgressIndicator(
                                color = MiniPlayerInk,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    if (playerState.isPlaying) {
                                        CommonR.drawable.iv_pause
                                    } else {
                                        CommonR.drawable.iv_play
                                    },
                                ),
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(18.dp),
                                tint = MiniPlayerInk,
                            )
                        }
                    }
                    IconButton(
                        onClick = onNextClick,
                        // 同上：不挂 sharedElement，避免 Tab 互切时跨页匹配漂移。
                        modifier = Modifier.size(29.dp),
                    ) {
                        Icon(
                            painter = painterResource(CommonR.drawable.ic_next),
                            contentDescription = "Next track",
                            modifier = Modifier.size(18.dp),
                            tint = MiniPlayerInk,
                        )
                    }
                }
                FoxLinearProgress(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = AccentTerracottaBright,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    height = 2.dp,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MiniPlayerPreview() {
    FoxMusicTheme {
        var count by remember { mutableStateOf(0) }
        SharedTransitionLayout {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { it } + fadeIn() togetherWith
                            slideOutVertically { -it } + fadeOut()
                    } else {
                        slideInVertically { -it } + fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
            ) { targetCount ->
                MiniPlayer(
                    playerState = PlayerState(
                        currentMusic = Music(
                            id = targetCount.toLong(),
                            title = "Sample Song",
                            url = "",
                            duration = 240000,
                        ),
                        isPlaying = true,
                        position = 60000,
                        duration = 240000,
                    ),
                    this@SharedTransitionLayout,
                    this,
                    onPlayPauseClick = {},
                    onNextClick = {},
                )
            }
        }
    }
}
