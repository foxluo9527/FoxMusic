package com.fox.music.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.foxHairline
import kotlin.math.abs
import kotlin.math.max

/**
 * 取色盘预设格（与歌词「高亮色预设」同一套纸感色，深浅两底都压得住）。
 */
val FoxColorPickerPresets: List<Color> = listOf(
    Color(0xFFB8513A),
    Color(0xFFC25E42),
    Color(0xFFB07624),
    Color(0xFF4E7A3C),
    Color(0xFF38708C),
    Color(0xFF38598C),
    Color(0xFF8C4E6E),
    Color(0xFF2E2E2C),
)

/**
 * 统一取色盘（v12 新增）：明度/饱和度面板 + 色相条 + 预设格 + 只读 HEX。
 *
 * 为什么替换掉原来的 HEX 手输框：让用户敲六位十六进制本身就是把「调色」降级成
 * 「填表」，而且校验失败时没有任何反馈。这里全部改成拖拽，所见即所得；
 * HEX 仅作只读展示（顺便当校验凭证），不承担输入职责。
 *
 * @param color 当前颜色（外部唯一数据源）
 * @param onColorChange 拖动/点预设时回调新颜色
 * @param caption 右侧提示文案，例如「已应用到「普通词」」
 * @param presets 预设格，传空列表即隐藏
 */
@Composable
fun FoxColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    presets: List<Color> = FoxColorPickerPresets,
    showHex: Boolean = true,
    panelHeight: Int = 110,
) {
    // ── HSV 本地态 ────────────────────────────────────────────────
    // 不能直接从 color 反推着用：色相在 s=0（纯白/灰）时数学上无定义，
    // 每帧反推会把用户刚拖好的色相弹回 0，面板就"跳"。
    val initial = remember { color.toHsv() }
    var hue by remember { mutableFloatStateOf(initial.h) }
    var sat by remember { mutableFloatStateOf(initial.s) }
    var value by remember { mutableFloatStateOf(initial.v) }

    // 外部换色（点预设 / 切主题）时把三个分量带过来；拖动过程中数值一致，不会打架。
    LaunchedEffect(color) {
        val hsv = color.toHsv()
        if (hsv.s > 0.002f && abs(hsv.h - hue) > 0.5f) hue = hsv.h
        if (abs(hsv.s - sat) > 0.004f) sat = hsv.s
        if (abs(hsv.v - value) > 0.004f) value = hsv.v
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 饱和度(x) × 明度(y) 面板
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight.dp)
                .clip(RoundedCornerShape(13.dp))
                .border(1.dp, foxHairline(), RoundedCornerShape(13.dp))
                .pickerDrag { pos, size ->
                    sat = (pos.x / size.width).coerceIn(0f, 1f)
                    value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    onColorChange(hsvToColor(hue, sat, value))
                },
        ) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hsvToColor(hue, 1f, 1f))))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = Color(0x66000000),
                radius = 8.5.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 色相条
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .pickerDrag { pos, size ->
                    hue = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                    // 纯白/纯黑时拖色相看不出变化——顺手把饱和度/明度抬到可见区间，
                    // 否则用户会以为"这条没反应"。
                    if (sat < 0.05f) sat = 0.55f
                    if (value < 0.05f) value = 0.9f
                    onColorChange(hsvToColor(hue, sat, value))
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        hsvToColor(0f, 1f, 1f),
                        hsvToColor(60f, 1f, 1f),
                        hsvToColor(120f, 1f, 1f),
                        hsvToColor(180f, 1f, 1f),
                        hsvToColor(240f, 1f, 1f),
                        hsvToColor(300f, 1f, 1f),
                        hsvToColor(360f, 1f, 1f),
                    ),
                ),
            )
            val cx = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(cx, size.height / 2f),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = Color(0x66000000),
                radius = 10.5.dp.toPx(),
                center = Offset(cx, size.height / 2f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        if (showHex || caption != null) {
            Spacer(modifier = Modifier.height(13.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showHex) {
                    Text(
                        text = color.toHexUpper(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }

        if (presets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { preset ->
                    val selected = preset.toArgbInt() == color.toArgbInt()
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(preset)
                            .border(1.dp, foxHairline(), CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.padding(3.dp).border(2.dp, Color.White, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onColorChange(preset) },
                    )
                }
            }
        }
    }
}

/** 拖动/点按统一处理：按下即生效，之后逐帧跟随，抬手结束。 */
private inline fun Modifier.pickerDrag(
    crossinline onPosition: (Offset, Size) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPosition(down.position, size.toSize())
            down.consume()
            var event = awaitPointerEvent()
            while (event.changes.any { it.pressed }) {
                event.changes.forEach { change ->
                    if (change.pressed) {
                        onPosition(change.position, size.toSize())
                        change.consume()
                    }
                }
                event = awaitPointerEvent()
            }
        }
    }
}

private fun IntSize.toSize(): Size = Size(width.toFloat(), height.toFloat())

/** ARGB / HSV 互转（commonMain 没有 android.graphics，全部自己算）。 */
internal data class Hsv(val h: Float, val s: Float, val v: Float)

internal fun Color.toHsv(): Hsv {
    val r = red
    val g = green
    val b = blue
    val maxC = max(max(r, g), b)
    val minC = minOf(r, g, b)
    val d = maxC - minC
    var h = when {
        d < 1e-6f -> 0f
        maxC == r -> ((g - b) / d) % 6f
        maxC == g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    h *= 60f
    if (h < 0f) h += 360f
    val s = if (maxC <= 1e-6f) 0f else d / maxC
    return Hsv(h, s, maxC)
}

internal fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs((hh % 2f) - 1f))
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/** 0xFFRRGGBB / Long 值。 */
internal fun Color.toArgbInt(): Int {
    fun c(f: Float): Int = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
}

/** "#RRGGBB"（只读展示）。 */
fun Color.toHexUpper(): String {
    fun c(f: Float): String =
        (f * 255f + 0.5f).toInt().coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
    return "#${c(red)}${c(green)}${c(blue)}"
}
