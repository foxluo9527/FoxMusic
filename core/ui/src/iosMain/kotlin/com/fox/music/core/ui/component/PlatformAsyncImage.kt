package com.fox.music.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import org.jetbrains.skia.Image as SkiaImage

/**
 * iOS 侧图片加载：Ktor(Darwin) 下载 → Skia 解码 → Compose ImageBitmap。
 *
 * 没有用第三方 KMP 图片库，是为了避免再引入一条依赖链；
 * 当前只需要「按 URL 拉网络图」这一条路径，缓存用最简内存 Map 即可
 * （进程内有效，与 Android 端 Coil 的内存缓存语义相当）。
 */
private val imageHttpClient = HttpClient(Darwin)

private val imageCache = HashMap<String, ImageBitmap>()

@Composable
actual fun PlatformAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onError: () -> Unit,
    loadingContent: @Composable BoxScope.() -> Unit,
    errorContent: @Composable BoxScope.() -> Unit,
) {
    // null = 加载中；ImageBitmap = 成功；Throwable = 失败
    var state by remember(url) { mutableStateOf<Any?>(imageCache[url]) }

    LaunchedEffect(url) {
        if (state is ImageBitmap) return@LaunchedEffect
        try {
            val bytes = imageHttpClient.get(url).readBytes()
            val bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            imageCache[url] = bitmap
            state = bitmap
        } catch (e: Throwable) {
            state = e
            onError()
        }
    }

    Box(modifier = modifier) {
        when (val current = state) {
            null -> loadingContent()
            is Throwable -> errorContent()
            is ImageBitmap -> Image(
                bitmap = current,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
