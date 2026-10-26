package com.fox.music.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.theme.foxHairline
import kotlin.math.roundToInt

/**
 * 全站滑杆（v12 新增）。
 *
 * 为什么不用 Material3 的 [androidx.compose.material3.Slider]：M3 的滑块是一根
 * **4dp 宽 × 44dp 高的竖向胶囊**，轨道也带一截粗底 + 滑块周围留缝，落在纸感界面上
 * 像一根竖着的红条，和设计稿「3dp 细线 + 16dp 圆形白钮 + 2dp 强调色描边」完全是
 * 两套语言（歌词字号栏被驳回就是这一条）。M3 Slider 的 thumb/track 形状没有可配
 * 参数，只能自绘。
 *
 * 规格：轨道 3dp 圆头（未选 = [foxHairline]，已选 = 主题强调色）、圆钮 16dp
 * （surface 填充 + 2dp 强调描边 + 轻投影）、整体触控高度 44dp。
 *
 * @param step 吸附步长，<=0 表示连续。字号这种整数场景传 1f，避免拖出 23.7sp。
 */
@Composable
fun FoxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float = 0f,
    enabled: Boolean = true,
    trackThickness: Dp = 3.dp,
    thumbDiameter: Dp = 16.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val inactive = foxHairline()
    val thumbFill = MaterialTheme.colorScheme.surface
    val thumbHalfPx = with(LocalDensity.current) { (thumbDiameter / 2).toPx() }

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun emitAt(x: Float, width: Float) {
        if (!enabled || width <= 0f) return
        // 圆钮圆心只能在 [半钮, 宽 - 半钮] 之间跑，映射区间要扣掉这两段，
        // 否则滑到两端时圆钮会有一半探出轨道外。
        val usable = (width - thumbHalfPx * 2f).coerceAtLeast(1f)
        val raw = ((x - thumbHalfPx) / usable).coerceIn(0f, 1f)
        var v = valueRange.start + raw * span
        if (step > 0f) v = (v / step).roundToInt() * step
        onValueChange(v.coerceIn(valueRange.start, valueRange.endInclusive))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .foxDragTo { position, size -> emitAt(position.x, size.width) }
            .then(if (enabled) Modifier else Modifier.alpha(0.4f)),
    ) {
        val centerY = size.height / 2f
        val half = thumbDiameter.toPx() / 2f
        val left = half
        val right = size.width - half
        val centerX = left + (right - left) * fraction

        // 未选轨道
        drawLine(
            color = inactive,
            start = Offset(left, centerY),
            end = Offset(right, centerY),
            strokeWidth = trackThickness.toPx(),
            cap = StrokeCap.Round,
        )
        // 已选轨道（从起点画到圆钮圆心，圆头自然被圆钮盖住）
        if (centerX > left + 0.5f) {
            drawLine(
                color = accent,
                start = Offset(left, centerY),
                end = Offset(centerX, centerY),
                strokeWidth = trackThickness.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // 轻投影（替代 box-shadow 0 1px 3px）：只画在圆钮下方 1dp，不参与布局
        drawCircle(
            color = Color(0x1F1A1512),
            radius = half + 0.5.dp.toPx(),
            center = Offset(centerX, centerY + 1.dp.toPx()),
        )
        // 圆钮：纸面填充 + 强调色描边
        drawCircle(color = thumbFill, radius = half, center = Offset(centerX, centerY))
        drawCircle(
            color = accent,
            radius = half,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
