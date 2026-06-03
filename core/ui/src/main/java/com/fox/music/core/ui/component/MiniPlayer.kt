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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.ui.theme.FoxMusicTheme

/**
 * 与 [com.fox.music.feature.player.ui.screen.PlayerScreen] 的共享元素过渡需在 overlay 中渲染；
 * 设为 false 会断开 MiniPlayer ↔ 详情页的 hero 动画。模糊背景已不挂 sharedBounds，Tab 可预测返回仍随页面移动。
 */
private const val MINI_PLAYER_SHARED_IN_OVERLAY = true

private val DefaultMiniPlayerContrastColor = Color(0xFF202122)

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
    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        ) {
            // 模糊背景 + Palette 取色，与播放详情页一致用 contrastColor 适配前景
            ImageWithPaletteColors(
                modifier = Modifier.matchParentSize(),
                url = music.coverImage,
                emitPlaceholderColors = cachedContrast == null,
                skipPaletteExtraction = cachedContrast != null,
                onColorsExtracted = { _: Int, contrast: Int ->
                    val color = Color(contrast)
                    MiniPlayerPaletteCache.putContrast(music.id, music.coverImage, color)
                    if (color != contrastColor) {
                        contrastColor = color
                    }
                },
            )
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .size(48.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    key = "music-image-${music.id}",
                                ),
                                animatedVisibilityScope = animatedContentScope,
                                renderInOverlayDuringTransition = MINI_PLAYER_SHARED_IN_OVERLAY,
                            ),
                        shape = MaterialTheme.shapes.small,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = music.title + "-" + music.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
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
                            text = music.getCurrentLyric(playerState.position) ?: "",
                            style = MaterialTheme.typography.bodyMedium,
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
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
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
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next track",
                            modifier = Modifier.size(28.dp),
                            tint = contrastColor,
                        )
                    }
                }
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
