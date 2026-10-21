package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.LyricsParser
import kotlin.math.roundToInt

/**
 * 试听面板：可拖动进度条 + 歌词跟随（音源纠错 / 收录 / 歌单导入共用）。
 *
 * 歌词传的是「原曲」的，用于边听候选音源边对照——音源是错的版本/别的歌时，
 * 歌词和听到的内容会立刻对不上。
 */
@Composable
fun PreviewLyricPanel(
    title: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    lyrics: List<LyricsParser.LyricLine>,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    artist: String = "",
    headerText: String = "原曲歌词 · 对照试听",
    emptyLyricText: String = "暂无歌词",
) {
    val duration = durationMs.coerceAtLeast(0L)
    val listState = rememberLazyListState()
    // 当前歌词行：跟随试听进度
    val currentIndex = lyrics.indexOfLast { positionMs >= it.startTimeMs }
    LaunchedEffect(currentIndex, lyrics.size) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (lyrics.isEmpty()) {
                    Text(
                        text = emptyLyricText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        itemsIndexed(
                            items = lyrics,
                            key = { index, _ -> index },
                        ) { index, line ->
                            val isCurrent = index == currentIndex
                            Text(
                                text = line.text,
                                style = if (isCurrent) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭试听")
                }
            }
            // 可拖动进度条：拖动即 seek，歌词跟随
            PreviewSeekBar(
                fraction = if (duration > 0) {
                    (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                },
                onSeekFraction = { fraction -> onSeek((fraction * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${formatPreviewTime(positionMs)} / ${formatPreviewTime(duration)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 试听进度条（纠错/收录/导入共用）。
 *
 * 不用默认 Slider：轨太高、拇指比轨高、值被钳到末尾、轨道末端圆角消失且与拇指有间隙。
 * 这里自定义：4dp 圆角细轨 + 14dp 小拇指、无间隙、点击/拖动均 seek。
 */
@Composable
fun PreviewSeekBar(
    fraction: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val thumbSize = 14.dp
    var trackWidthPx by remember { mutableStateOf(0f) }
    val thumbRadiusPx = with(LocalDensity.current) { thumbSize.toPx() / 2f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (trackWidthPx > 0f) {
                        onSeekFraction((down.position.x / trackWidthPx).coerceIn(0f, 1f))
                    }
                    drag(down.id) { change ->
                        if (trackWidthPx > 0f) {
                            onSeekFraction((change.position.x / trackWidthPx).coerceIn(0f, 1f))
                        }
                    }
                }
            },
        // 子项从左缘起排：轨道铺满，拇指按进度偏移
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(inactiveColor)
                .onSizeChanged { trackWidthPx = it.width.toFloat() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(activeColor),
            )
        }
        Box(
            modifier = Modifier
                .offset {
                    val x = (safeFraction * trackWidthPx - thumbRadiusPx)
                        .coerceIn(0f, (trackWidthPx - thumbRadiusPx * 2).coerceAtLeast(0f))
                    IntOffset(x.roundToInt(), 0)
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(activeColor),
        )
    }
}

fun formatPreviewTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
