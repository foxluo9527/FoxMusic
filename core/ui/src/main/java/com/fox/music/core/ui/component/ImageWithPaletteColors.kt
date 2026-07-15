package com.fox.music.core.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.ColorUtils.calculateLuminance
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.fox.music.core.ui.modifier.BlurTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 使用 Palette 提取主色与反差色（黑/白，保证可读对比度） */
fun extractColorsWithPalette(bitmap: Bitmap): Pair<Int, Int> {
    val palette = Palette.from(bitmap).generate()
    val dominantSwatch = palette.dominantSwatch
    val dominantColor = dominantSwatch?.rgb ?: Color.WHITE
    val contrastColor = calculateContrastColor(dominantColor)
    return Pair(dominantColor, contrastColor)
}

/**
 * 根据背景色亮度取反色：浅底用深字，深底用浅字。
 * @param backgroundColor 实际可见背景（可先与遮罩色混合）
 */
fun calculateContrastColor(backgroundColor: Int): Int {
    return if (calculateLuminance(backgroundColor) > 0.5) {
        Color.BLACK
    } else {
        Color.WHITE
    }
}

/**
 * 在 [backgroundColor] 上叠一层 [overlayColor]（[overlayAlpha]）后，再取反色。
 * MiniPlayer 有深色渐变遮罩时用此方法，与播放页「按背景取反」一致。
 */
fun contrastColorAgainstOverlay(
    backgroundColor: Int,
    overlayColor: Int = Color.BLACK,
    overlayAlpha: Float = 0.45f,
): Int {
    val effective = ColorUtils.blendARGB(
        backgroundColor,
        overlayColor,
        overlayAlpha.coerceIn(0f, 1f),
    )
    return calculateContrastColor(effective)
}

@Composable
fun ImageWithPaletteColors(
    modifier: Modifier,
    url: String?,
    onColorsExtracted: (dominantColor: Int, contrastColor: Int) -> Unit,
    /** 为 false 时不于每次重组时先回调占位色，避免已有缓存时切换 Tab 出现颜色跳变 */
    emitPlaceholderColors: Boolean = true,
    /** 为 true 时跳过 Palette 提取（例如 MiniPlayer 已有缓存的 contrast 色） */
    skipPaletteExtraction: Boolean = false,
) {
    LaunchedEffect(url, emitPlaceholderColors) {
        if (emitPlaceholderColors) {
            onColorsExtracted("#f6f7f9".toColorInt(), "#202122".toColorInt())
        }
    }
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(processUrl(url))
            .transformations(BlurTransformation(radius = 90, scale = 1f))
            .diskCacheKey("$url?cacheBlur")
            .memoryCacheKey("$url?cacheBlur")
            .build(),
    )

    LaunchedEffect(painter.state, url, skipPaletteExtraction) {
        if (skipPaletteExtraction) return@LaunchedEffect
        if (painter.state is AsyncImagePainter.State.Success) {
            val result = (painter.state as AsyncImagePainter.State.Success).result
            val drawable = result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bitmap = drawable.bitmap
                val colors = withContext(Dispatchers.Default) {
                    extractColorsWithPalette(bitmap)
                }
                onColorsExtracted(colors.first, colors.second)
            }
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

private fun processUrl(url: String?): String? =
    com.fox.music.core.common.util.MediaUrlResolver.resolve(url)
