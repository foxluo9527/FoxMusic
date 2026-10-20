package com.fox.music.feature.stream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 音流页封面预热与主色采样（Android 专用，仅依赖 App 级 Coil ImageLoader）。
 *
 * - [preloadStreamCover]：预热后两页封面图，翻页时直接命中内存/磁盘缓存。
 * - [streamCoverColor]：把封面缩小到 [SAMPLE_SIZE]² 后取「高饱和主色」，作为页面渐变底色；
 *   结果进 [LruCache]，按封面 URL 只计算一次。
 *
 * 取色口径对齐汽水音乐：底色应是与封面同色相的**中高饱和**颜色（如红色封面 → 深红页面），
 * 而不是把整图平均后压暗到近黑（旧实现 1×1 采样 + 0.45 压暗，导致页面发灰发黑）。
 *
 * URL 为空或任何异常都静默兜底返回 null，由调用方回落主题色，保证不崩。
 */

/** 底色缓存条目上限（音流队列 30 首，64 足够覆盖前后若干页） */
private const val COVER_COLOR_CACHE_SIZE = 64

/** 采样分辨率：正方形缩略图，兼顾取色质量与耗时 */
private const val SAMPLE_SIZE = 48

/** 无彩色可采、回落整图均值时的压暗系数 */
private const val COVER_COLOR_FALLBACK_DARKEN = 0.5f

/** 目标饱和/明度：默认「高饱和 + 中等明度」，接近汽水的封面主色氛围 */
private const val TARGET_SATURATION = 0.55f
private const val TARGET_VALUE = 0.58f

/** 参与统计的像素门槛：太灰、太黑、太白的像素不代表封面主色 */
private const val MIN_SATURATION = 0.15f
private const val MIN_VALUE = 0.12f
private const val MAX_VALUE = 0.95f

private val coverColorCache = LruCache<String, Int>(COVER_COLOR_CACHE_SIZE)

/** 预热封面：以近似实际展示尺寸请求，命中 AsyncImage 的内存缓存，翻页即时渲染 */
fun preloadStreamCover(context: Context, url: String?, sizePx: Int) {
    val key = url?.takeIf { it.isNotBlank() } ?: return
    try {
        val request = ImageRequest.Builder(context)
            .data(key)
            .size(sizePx.coerceAtLeast(1))
            .build()
        context.imageLoader.enqueue(request)
    } catch (_: Throwable) {
        // 预热失败不影响主流程
    }
}

/**
 * 取封面底色（汽水式高饱和主色 ARGB）。首次计算走 IO 线程缩放采样，之后命中 [coverColorCache]。
 * 无法取色（空 URL / 加载失败 / 非位图）时返回 null。
 */
suspend fun streamCoverColor(context: Context, url: String?): Int? {
    val key = url?.takeIf { it.isNotBlank() } ?: return null
    coverColorCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(key)
                .size(SAMPLE_SIZE, SAMPLE_SIZE)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build()
            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                ?.drawable
                ?.let { it as? BitmapDrawable }
                ?.bitmap
                ?: return@withContext null
            if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
            val color = bitmap.vibrantBackgroundColor()
            coverColorCache.put(key, color)
            color
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * 提取「高饱和主色」：
 * 1) 跳过透明/近黑/近白/低饱和像素，其余按 `饱和 × 明度` 加权求平均，得到封面真正有色彩的那部分；
 * 2) 把结果拉高到目标饱和度与明度（保留原始色相），得到汽水式的浓郁底色。
 * 若封面本身无彩色（黑白图），退回整图均值并压暗，避免给灰图硬加颜色。
 */
private fun Bitmap.vibrantBackgroundColor(): Int {
    val hsv = FloatArray(3)
    var weightSum = 0.0
    var rWeighted = 0.0
    var gWeighted = 0.0
    var bWeighted = 0.0
    var totalCount = 0.0
    var rTotal = 0.0
    var gTotal = 0.0
    var bTotal = 0.0

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = getPixel(x, y)
            if (((pixel ushr 24) and 0xFF) < 128) continue
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            rTotal += r
            gTotal += g
            bTotal += b
            totalCount += 1.0

            rgbToHsv(r / 255f, g / 255f, b / 255f, hsv)
            if (hsv[1] < MIN_SATURATION || hsv[2] < MIN_VALUE || hsv[2] > MAX_VALUE) continue
            val weight = (hsv[1] * hsv[2]).toDouble()
            rWeighted += r * weight
            gWeighted += g * weight
            bWeighted += b * weight
            weightSum += weight
        }
    }

    val hasColor = weightSum > 0.0
    val (r, g, b) = when {
        hasColor -> Triple(
            (rWeighted / weightSum).toFloat(),
            (gWeighted / weightSum).toFloat(),
            (bWeighted / weightSum).toFloat(),
        )
        totalCount > 0.0 -> {
            val f = COVER_COLOR_FALLBACK_DARKEN
            Triple(
                (rTotal / totalCount * f).toFloat(),
                (gTotal / totalCount * f).toFloat(),
                (bTotal / totalCount * f).toFloat(),
            )
        }
        else -> return FALLBACK_COLOR
    }

    rgbToHsv(r / 255f, g / 255f, b / 255f, hsv)
    if (!hasColor) {
        // 黑白封面：不给灰底硬上色
        return hsvToArgb(hsv[0], hsv[1], hsv[2])
    }
    return hsvToArgb(hsv[0], maxOf(hsv[1], TARGET_SATURATION).coerceAtMost(0.9f), TARGET_VALUE)
}

/** 全透明/空图的兜底底色（近黑中性色） */
private const val FALLBACK_COLOR = 0xFF17171A.toInt()

private fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val out = FloatArray(3)
    hsvToRgb(h, s, v, out)
    val r = (out[0] * 255f).toInt().coerceIn(0, 255)
    val g = (out[1] * 255f).toInt().coerceIn(0, 255)
    val b = (out[2] * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun rgbToHsv(r: Float, g: Float, b: Float, out: FloatArray) {
    val maxValue = maxOf(r, g, b)
    val minValue = minOf(r, g, b)
    val delta = maxValue - minValue
    out[2] = maxValue
    out[1] = if (maxValue <= 0f) 0f else delta / maxValue
    out[0] = when {
        delta == 0f -> 0f
        maxValue == r -> 60f * (((g - b) / delta) % 6f)
        maxValue == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
}

private fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val r: Float
    val g: Float
    val b: Float
    when {
        h < 60f -> { r = c; g = x; b = 0f }
        h < 120f -> { r = x; g = c; b = 0f }
        h < 180f -> { r = 0f; g = c; b = x }
        h < 240f -> { r = 0f; g = x; b = c }
        h < 300f -> { r = x; g = 0f; b = c }
        else -> { r = c; g = 0f; b = x }
    }
    out[0] = r + m
    out[1] = g + m
    out[2] = b + m
}
