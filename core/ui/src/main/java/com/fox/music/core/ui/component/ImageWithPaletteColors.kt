package com.fox.music.core.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils.calculateLuminance
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.fox.music.core.ui.modifier.BlurTransformation

// 使用 Palette API 提取颜色
fun extractColorsWithPalette(bitmap: Bitmap): Pair<Int, Int> {
    val palette = Palette.from(bitmap).generate()

    // 获取主体色
    val dominantSwatch = palette.dominantSwatch
    val dominantColor = dominantSwatch?.rgb ?: Color.WHITE

    // 获取合适的反差色
    val contrastColor = dominantSwatch?.bodyTextColor ?: calculateContrastColor(dominantColor)

    return Pair(dominantColor, contrastColor)
}

// 计算反差色
private fun calculateContrastColor(color: Int): Int {
    // 计算颜色的亮度（使用相对亮度公式）
    val luminance = calculateLuminance(color)

    // 根据亮度选择白色或黑色作为反差色
    return if (luminance > 0.5) {
        Color.BLACK
    } else {
        Color.WHITE
    }
}

// 在 Compose 中使用
@Composable
fun ImageWithPaletteColors(
    modifier: Modifier,
    url: String?,
    onColorsExtracted: (dominantColor: Int, contrastColor: Int) -> Unit,
) {
    onColorsExtracted("#f6f7f9".toColorInt(), "#333333".toColorInt())
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(processUrl(url))
            .transformations(BlurTransformation(radius = 90, scale = 1f))
            .diskCacheKey("$url?cacheBlur")
            .memoryCacheKey("$url?cacheBlur")
            .build()
    )

    // 当图片加载成功时提取颜色
    LaunchedEffect(painter.state) {
        if (painter.state is AsyncImagePainter.State.Success) {
            val result = (painter.state as AsyncImagePainter.State.Success).result
            val drawable = result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bitmap = drawable.bitmap
                val colors = extractColorsWithPalette(bitmap)
                onColorsExtracted(colors.first, colors.second)
            }
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

private fun processUrl(url: String?): String? {
    return url?.let {
        if (it.startsWith("http")) it else "http://39.106.30.151:9000$it"
    }
}