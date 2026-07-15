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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fox.music.core.common.R as CommonR
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.ui.theme.FoxMusicTheme
import com.fox.music.core.ui.theme.MiniPlayerShape
import com.fox.music.core.ui.theme.FoxSemanticColors

/**
 * 与 [com.fox.music.feature.player.ui.screen.PlayerScreen] 的共享元素过渡需在 overlay 中渲染；
 * 设为 false 会断开 MiniPlayer ↔ 详情页的 hero 动画。模糊背景已不挂 sharedBounds，Tab 可预测返回仍随页面移动。
 */
private const val MINI_PLAYER_SHARED_IN_OVERLAY = true

private val DefaultMiniPlayerContrastColor = Color(0xFFF3EDE3)

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
    val cachedContrast = MiniPlayerPaletteCache.getContrast(music.id, music.coverImage)
    var contrastColor by remember(music.id, music.coverImage) {
        mutableStateOf(cachedContrast ?: DefaultMiniPlayerContrastColor)
    }
    val lyricColor = contrastColor.copy(alpha = 0.78f)
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
                .padding(horizontal = 12.dp)
                .clip(MiniPlayerShape)
                .clickable(onClick = onClick),
        ) {
            ImageWithPaletteColors(
                modifier = Modifier.matchParentSize(),
                url = music.coverImage,
                emitPlaceholderColors = cachedContrast == null,
                skipPaletteExtraction = cachedContrast != null,
                onColorsExtracted = { dominant: Int, _: Int ->
                    // 与播放页一致：按「可见背景」取反色；MiniPlayer 有深色遮罩，先混合再取反
                    val color = Color(
                        contrastColorAgainstOverlay(
                            backgroundColor = dominant,
                            overlayColor = android.graphics.Color.BLACK,
                            overlayAlpha = 0.45f,
                        ),
                    )
                    MiniPlayerPaletteCache.putContrast(music.id, music.coverImage, color)
                    if (color != contrastColor) {
                        contrastColor = color
                    }
                },
            )
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.35f),
                                FoxSemanticColors.amberGlow.copy(alpha = 0.2f),
                            ),
                        ),
                    ),
            )
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .size(52.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = "music-image-${music.id}",
                                ),
                                animatedVisibilityScope = animatedContentScope,
                                renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                            ),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = music.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = contrastColor,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = lyricColor,
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
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(
                                key = "music-toggle-${music.id}",
                            ),
                            animatedVisibilityScope = animatedContentScope,
                            renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                        ),
                    ) {
                        if (playerState.isLoading) {
                            CircularProgressIndicator(
                                color = contrastColor,
                                modifier = Modifier.size(28.dp),
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
                                modifier = Modifier.size(30.dp),
                                tint = contrastColor,
                            )
                        }
                    }
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(
                                key = "music-next-${music.id}",
                            ),
                            animatedVisibilityScope = animatedContentScope,
                            renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(CommonR.drawable.ic_next),
                            contentDescription = "Next track",
                            modifier = Modifier.size(26.dp),
                            tint = contrastColor,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
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
