package com.fox.music.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LockScreenLyricScreen(
    playerState: PlayerState,
    positionMs: Long,
    onDismiss: () -> Unit,
    onRepeatClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onTogglePlayClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    enableSwipeToDismiss: Boolean = true,
    showSwipeHint: Boolean = true,
    lyricHighlightColor: Color? = null,
    lyricNormalColor: Color? = null,
) {
    val music = playerState.currentMusic
    val lyricColors = rememberLyricColors()
    val highlightColor = lyricHighlightColor ?: lyricColors.highlightComposeColor()
    val normalColor = lyricNormalColor ?: lyricColors.textComposeColor()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = screenHeightPx * 0.22f
    val dragOffsetY = remember { Animatable(0f) }
    val dismissProgress = ((-dragOffsetY.value) / screenHeightPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (enableSwipeToDismiss) {
                    Modifier.pointerInput(screenHeightPx, dismissThresholdPx) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (dragOffsetY.value < -dismissThresholdPx) {
                                        dragOffsetY.animateTo(
                                            targetValue = -screenHeightPx,
                                            animationSpec = tween(
                                                durationMillis = (220 * (1f - dismissProgress)).roundToInt()
                                                    .coerceAtLeast(120),
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                        onDismiss()
                                    } else {
                                        dragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(),
                                    )
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                scope.launch {
                                    dragOffsetY.snapTo(
                                        (dragOffsetY.value + dragAmount).coerceAtMost(0f),
                                    )
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .graphicsLayer {
                alpha = 1f - dismissProgress * 0.35f
            },
    ) {
        LockScreenLyricBackground(
            coverUrl = music?.coverImage,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LockScreenClock(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(0.35f))
            if (music == null) {
                Text(
                    text = "暂无播放",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                )
            } else {
                LockScreenMusicSection(
                    music = music,
                    positionMs = positionMs,
                    lyricHighlightColor = highlightColor,
                    lyricNormalColor = normalColor,
                )
            }
            Spacer(Modifier.height(20.dp))
            PlaybackControlBar(
                playerState = playerState,
                onRepeatClick = onRepeatClick,
                onPreviousClick = onPreviousClick,
                onTogglePlayClick = onTogglePlayClick,
                onNextClick = onNextClick,
                onFavoriteClick = onFavoriteClick,
                iconTint = Color.White,
                favoriteTint = if (playerState.isFavorite) Color(0xFFFF5252) else Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(0.35f))
            if (showSwipeHint && enableSwipeToDismiss) {
                SwipeUpDismissHint(
                    modifier = Modifier.padding(bottom = 16.dp),
                    progress = dismissProgress,
                )
            }
        }
    }
}

@Composable
private fun LockScreenLyricBackground(
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (!coverUrl.isNullOrBlank()) {
            BlurBgImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1B1B2F),
                                Color(0xFF0D0D14),
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.35f to Color.Black.copy(alpha = 0.45f),
                            0.7f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun LockScreenClock(modifier: Modifier = Modifier) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L.milliseconds)
        }
    }
    val zoned = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
    val timeText = zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    val dateText = zoned.format(
        DateTimeFormatter.ofPattern("EEEE M月d日", Locale.CHINA),
    )
    Column(
        modifier = modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.W200,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateText,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun LockScreenMusicSection(
    music: Music,
    positionMs: Long,
    lyricHighlightColor: Color,
    lyricNormalColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier
                .size(168.dp)
                .shadow(24.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = music.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = music.artists.joinToString { it.name },
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        LockScreenLyricLines(
            music = music,
            positionMs = positionMs,
            lyricHighlightColor = lyricHighlightColor,
            lyricNormalColor = lyricNormalColor,
        )
    }
}

@Composable
private fun LockScreenLyricLines(
    music: Music,
    positionMs: Long,
    lyricHighlightColor: Color,
    lyricNormalColor: Color,
) {
    val lines = music.lyricLines.map { it.text }
    if (lines.isEmpty()) {
        Text(
            text = "暂无歌词",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        return
    }
    val currentIndex = music.lyricLines.indexOfLast { it.startTimeMs <= positionMs }.coerceAtLeast(0)
    val start = (currentIndex - 2).coerceAtLeast(0)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        lines.drop(start).take(5).forEachIndexed { index, line ->
            val lineIndex = start + index
            val isCurrent = lineIndex == currentIndex
            Text(
                text = line,
                color = if (isCurrent) lyricHighlightColor else lyricNormalColor.copy(alpha = 0.72f),
                fontSize = if (isCurrent) 19.sp else 15.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = if (isCurrent) 28.sp else 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SwipeUpDismissHint(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_hint")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    val hintAlpha = (1f - progress * 2.5f).coerceIn(0f, 1f)
    Column(
        modifier = modifier.graphicsLayer {
            alpha = hintAlpha
            translationY = bounceOffset
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "向上滑动退出",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
        )
    }
}
