package com.fox.music.feature.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.util.formatTrackDuration
import kotlin.math.roundToInt

/**
 * 播放进度。
 *
 * 2026-10 改版（原型 03 播放页 .pl-prog）：
 * - 轨道 3dp、已播段用强调色、未播段是 14% 的墨色；时间标签在轨道下方两端。
 * - 滑块 11dp：白环 + 强调色实心，圆心永远压在轨道中线上。
 *
 * 自绘而非 M3 Slider：M3 的 track/thumb 走 Layout 测量（各自 (总高-自身高)/2 居中），
 * 垂直对齐依赖测量细节，用户机上曾出现滑块高出轨道约 2dp。这里轨道与滑块放进同一个
 * 垂直居中的 Box，滑块水平位置显式算成「内缩量 + 进度×可行程」，从构造上保证对齐。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongProgress(
    modifier: Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    contrastColor: Color,
) {
    val position by musicController.currentPosition.collectAsState(0L)
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // 拖动过程中的临时位置（松手才真正 seek）
    var dragValue by remember { mutableFloatStateOf(-1f) }
    val durationMs = playerState.duration.coerceAtLeast(1L).toFloat()
    val currentValue = if (dragValue >= 0f) dragValue else position.toFloat()
    val fraction = (currentValue / durationMs).coerceIn(0f, 1f)

    // 轨道左右各内缩半个滑块，和滑块行程对齐（Slider 的经典做法）
    val thumbRadius = 5.5.dp
    val thumbSize = 11.dp
    val trackHeight = 3.dp
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val travelPx = (trackWidthPx - thumbRadiusPx * 2).coerceAtLeast(0f)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp) // 触摸目标比视觉轨道大，好抓
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(durationMs, trackWidthPx) {
                    if (travelPx <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun valueAt(x: Float): Float =
                            (((x - thumbRadiusPx) / travelPx).coerceIn(0f, 1f)) * durationMs
                        dragValue = valueAt(down.position.x)
                        var change: PointerInputChange? = down
                        while (change != null && change.pressed) {
                            dragValue = valueAt(change.position.x)
                            change.consume()
                            change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        }
                        musicController.seekTo(dragValue.toLong())
                        dragValue = -1f
                    }
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    contentDescription = "播放进度"
                    setProgress { target ->
                        val v = target.coerceIn(0f, 1f) * durationMs
                        musicController.seekTo(v.toLong())
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 轨道底：未播段（内缩 thumbRadius，和滑块行程对齐）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = thumbRadius)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(contrastColor.copy(alpha = 0.14f)),
            )
            // 已播段：从左端起，宽度按进度
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = thumbRadius)
                        .fillMaxWidth(fraction)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
            }
            // 滑块：圆心 = 内缩量 + 进度×可行程，永远压在轨道中线上
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val x = (thumbRadiusPx + travelPx * fraction - thumbSizePx / 2f)
                            .roundToInt()
                        IntOffset(x, 0)
                    }
                    .size(thumbSize)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(accent, CircleShape),
            )
        }
        // 时长未知（duration<=0）时两条标签都不画，避免出现「只有右边一个数」的怪样子；
        // 位置为 0 时 formatTrackDuration 会返回空串（那是给"未知时长"用的），进度条这里应当显示 0:00。
        val rightText = formatTrackDuration(playerState.duration)
        val currentMs = if (dragValue >= 0f) dragValue.toLong() else position
        val leftText = if (currentMs <= 0L) "0:00" else formatTrackDuration(currentMs)
        if (rightText.isNotEmpty()) Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-6).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = leftText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = contrastColor.copy(alpha = 0.62f),
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = rightText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = contrastColor.copy(alpha = 0.62f),
            )
        }
    }
}
