package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 语音气泡喇叭图标的静止态波纹条数（= 未播放时的「正常样式」，与 Material volume_up 同形）。 */
private const val VOICE_WAVE_IDLE = 2

/** 播放态波纹帧数：0（无波纹）→ 1（一道）→ 2（两道）→ 0 ... */
private const val VOICE_WAVE_FRAMES = 3

/** 每帧停留时长：约 0.34s 一帧，走完一轮 ≈ 1s。 */
private const val VOICE_WAVE_FRAME_MS = 340L

/**
 * 语音气泡的喇叭图标，播放时波纹循环 0 → 1 → 2 → 0。
 *
 * 未播放（[isPlaying] = false）时固定为 2 道波纹的静止喇叭，播放结束后回到该样式。
 * 图形用 Canvas 自绘（左方块 + 梯形喇叭 + 右侧圆弧波纹），与 Material `volume_up`
 * 同形，且能按帧控制波纹条数 —— 纯矢量图标做不到「逐条出现」。
 */
@Composable
fun VoicePlayingIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    var waveFrame by remember { mutableIntStateOf(VOICE_WAVE_IDLE) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            // 播完/停播 → 复位成静止样式
            waveFrame = VOICE_WAVE_IDLE
            return@LaunchedEffect
        }
        var next = 0
        while (true) {
            waveFrame = next
            next = (next + 1) % VOICE_WAVE_FRAMES
            delay(VOICE_WAVE_FRAME_MS)
        }
    }

    VoiceWaveIcon(
        waveCount = waveFrame,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * 自绘喇叭 + 波纹。
 *
 * @param waveCount 波纹条数，0~2（超出会自动收敛）。
 */
@Composable
fun VoiceWaveIcon(
    waveCount: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // 以 24×24 的图标栅格为单位换算，保证任意 size 下比例一致
        val u = size.minDimension / 24f

        // 喇叭本体：左侧方块 + 右侧梯形（Material volume_up 的同款形状）
        drawRect(
            color = tint,
            topLeft = Offset(3.5f * u, 9f * u),
            size = Size(4f * u, 6f * u),
        )
        drawPath(
            path = Path().apply {
                moveTo(7.5f * u, 9f * u)
                lineTo(12.5f * u, 4f * u)
                lineTo(12.5f * u, 20f * u)
                lineTo(7.5f * u, 15f * u)
                close()
            },
            color = tint,
        )

        // 波纹：以 (14,12) 为圆心、半径 3 / 6 的两段圆弧，按帧从内到外逐条出现
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
        val waves = waveCount.coerceIn(0, VOICE_WAVE_IDLE)
        for (index in 0 until waves) {
            val radius = (3f + index * 3f) * u
            drawArc(
                color = tint,
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(14f * u - radius, 12f * u - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
    }
}
