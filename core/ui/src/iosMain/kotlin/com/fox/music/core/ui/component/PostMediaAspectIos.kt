package com.fox.music.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.social.PostMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * iOS 侧「按 URL 探测图片宽高比」：Ktor(Darwin) 下载 → Skia 解码取宽高。
 *
 * 与 [PlatformAsyncImage] 同一条依赖链（Ktor + Skia），没有引入新依赖。
 * Android 对应实现在 androidMain 的 ImageAspectRatio.kt（用 Coil 拿 drawable 尺寸）。
 *
 * 视频：优先用封面图（imageCoverUrl）探测；拿不到封面（或探测失败）就退回
 * [VIDEO_NO_COVER_FALLBACK_ASPECT]（16:9）并标记就绪 —— Skia 解不了 mp4，
 * 硬等一个永远不会来的结果会让整块媒体区卡在加载态高度（详见 PostMediaAspect.kt）。
 */
private val aspectHttpClient = HttpClient(Darwin)

private val iosAspectRatioCache = HashMap<String, Float>()

private suspend fun probeImageAspect(url: String?, fallback: Float): Pair<Float, Boolean> =
    withContext(Dispatchers.Default) {
        val resolved = url?.let { MediaUrlResolver.resolve(it) ?: it }
        if (resolved.isNullOrBlank()) return@withContext fallback to false
        iosAspectRatioCache[resolved]?.let { return@withContext it to true }
        runCatching {
            val bytes = aspectHttpClient.get(resolved).readBytes()
            val image = SkiaImage.makeFromEncoded(bytes)
            val w = image.width
            val h = image.height
            if (w > 0 && h > 0) {
                val aspect = w.toFloat() / h.toFloat()
                iosAspectRatioCache[resolved] = aspect
                aspect to true
            } else {
                fallback to false
            }
        }.getOrDefault(fallback to false)
    }

private fun PostMedia.aspectKey(): String = "${type}:${aspectRatioSourceUrl}"

@Composable
actual fun rememberPostMediaAspectRatioLoaded(
    media: PostMedia,
    fallback: Float,
    min: Float,
    max: Float,
): Pair<Float, Boolean> {
    // 无可用封面的视频：Skia 解不了 mp4，探测注定失败，直接用声明式兜底比例。
    // 顺带省掉一次「把整个 mp4 下载下来再解码失败」的浪费。
    val declaredAspect = media.noCoverFallbackAspectOrNull()
    if (declaredAspect != null) {
        return declaredAspect.coerceIn(min, max) to true
    }
    val cacheKey = remember(media) { media.aspectKey() }
    val cachedRaw = iosAspectRatioCache[cacheKey]
    var aspect by remember(cacheKey, min, max) {
        mutableFloatStateOf(cachedRaw?.coerceIn(min, max) ?: fallback)
    }
    var isReady by remember(cacheKey) { mutableStateOf(cachedRaw != null) }
    LaunchedEffect(cacheKey, fallback, min, max) {
        val fromCache = iosAspectRatioCache[cacheKey]
        if (fromCache != null) {
            aspect = fromCache.coerceIn(min, max)
            isReady = true
            return@LaunchedEffect
        }
        val url = media.imageCoverUrl ?: media.aspectRatioSourceUrl
        val (probed, hasDimensions) = probeImageAspect(url, fallback)
        if (hasDimensions) {
            iosAspectRatioCache[cacheKey] = probed
            aspect = probed.coerceIn(min, max)
            isReady = true
        } else {
            // 探测失败：视频退回声明式兜底比例（封面缺失或打不开都算），
            // 不让一个探测不出来的 item 把整块媒体区钉在加载态高度。
            val declared = media.videoDeclaredAspectOrNull()
            if (declared != null) {
                aspect = declared.coerceIn(min, max)
                isReady = true
            } else {
                isReady = false
            }
        }
    }
    return aspect to isReady
}

/**
 * 容器高度用哪个比例：优先真实探测值（同一轮播取最高/最矮）；一个都测不到时
 * 才退回声明式兜底（例如整帖只有一个视频），避免整块媒体区停在加载态高度。
 */
@Composable
actual fun rememberPostMediaListAspectRatioLoaded(
    mediaList: List<PostMedia>,
    pickShortest: Boolean,
    fallback: Float,
    min: Float,
    max: Float,
): Pair<Float, Boolean> {
    val cacheKeys = remember(mediaList) { mediaList.map { it.aspectKey() } }
    // 声明式兜底比例（无可用封面的视频）：只用于该 item 自身排版，
    // 不参与「容器高度」计算 —— 否则会把整轮播的容器按一个猜的比例定高。
    val declaredAspects = remember(mediaList) { mediaList.map { it.noCoverFallbackAspectOrNull() } }
    val allCached = cacheKeys.isNotEmpty() && cacheKeys.all { iosAspectRatioCache[it] != null }
    var aspect by remember(cacheKeys, pickShortest, min, max) {
        mutableFloatStateOf(
            if (allCached) {
                val values = cacheKeys.mapNotNull { iosAspectRatioCache[it] }
                (if (pickShortest) values.maxOrNull() else values.minOrNull())
                    ?.coerceIn(min, max) ?: fallback
            } else {
                fallback
            },
        )
    }
    var isReady by remember(cacheKeys) { mutableStateOf(cacheKeys.isEmpty() || allCached) }
    LaunchedEffect(cacheKeys, pickShortest, fallback, min, max) {
        if (mediaList.isEmpty()) {
            aspect = fallback
            isReady = true
            return@LaunchedEffect
        }
        val ratios = mutableListOf<Float>()
        val declaredRatios = mutableListOf<Float>()
        var unresolved = false
        mediaList.forEachIndexed { index, media ->
            val cacheKey = cacheKeys[index]
            val cached = iosAspectRatioCache[cacheKey]
            if (cached != null) {
                ratios += cached
                return@forEachIndexed
            }
            val declared = declaredAspects[index]
            if (declared != null) {
                declaredRatios += declared
                return@forEachIndexed
            }
            val (probed, hasDimensions) = probeImageAspect(media.aspectRatioSourceUrl, fallback)
            if (hasDimensions) {
                iosAspectRatioCache[cacheKey] = probed
                ratios += probed
            } else {
                // 视频探测失败只记声明式兜底；图片探测失败才算真正「未解析」
                val declared = media.videoDeclaredAspectOrNull()
                if (declared != null) declaredRatios += declared else unresolved = true
            }
        }
        val containerRatios = ratios.ifEmpty { declaredRatios }
        if (containerRatios.isNotEmpty()) {
            aspect = (if (pickShortest) containerRatios.maxOrNull() else containerRatios.minOrNull())
                ?.coerceIn(min, max) ?: fallback
        }
        isReady = !unresolved && containerRatios.isNotEmpty()
    }
    return aspect to isReady
}
