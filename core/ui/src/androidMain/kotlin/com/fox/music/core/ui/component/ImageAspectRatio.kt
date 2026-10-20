package com.fox.music.core.ui.component

import android.content.Context
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.common.util.probeVideoAspectRatio
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val aspectRatioCache = LruCache<String, Float>(256)

// 常量与 clamp 已下沉到 commonMain 的 PostMediaAspect.kt（两端共用同一套比例约束）：
// PostFeedMediaAspectRatio / clampFeedMediaAspectRatio。

/**
 * 根据图片 URL 解析真实宽高比（宽/高）。加载完成前返回 [fallback]。
 * [min]/[max] 用于避免极端比例撑破布局。
 */
@Composable
fun rememberImageAspectRatio(
    url: String?,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Float {
    val (aspect, _) = rememberImageAspectRatioLoaded(url, fallback, min, max)
    return aspect
}

/**
 * 返回 (aspectRatio, isReady)。isReady 为 true 表示已探测完成或命中缓存。
 */
@Composable
fun rememberImageAspectRatioLoaded(
    url: String?,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Pair<Float, Boolean> {
    val cachedRaw = url?.let { aspectRatioCache.get(it) }
    var aspect by remember(url, min, max) {
        mutableFloatStateOf(cachedRaw?.coerceIn(min, max) ?: fallback)
    }
    var isReady by remember(url) { mutableStateOf(cachedRaw != null || url.isNullOrBlank()) }
    val context = LocalContext.current
    LaunchedEffect(url, min, max) {
        if (url.isNullOrBlank()) {
            isReady = true
            return@LaunchedEffect
        }
        val fromCache = aspectRatioCache.get(url)
        if (fromCache != null) {
            aspect = fromCache.coerceIn(min, max)
            isReady = true
            return@LaunchedEffect
        }
        val (probed, hasDimensions) = probeImageAspectRatio(context, url, fallback)
        if (hasDimensions) {
            aspectRatioCache.put(url, probed)
            aspect = probed.coerceIn(min, max)
        }
        isReady = hasDimensions
    }
    return aspect to isReady
}

/**
 * 多图场景：取「同宽下最矮」的比例，即 max(宽/高)。
 * 用于详情 pager 固定高度，避免左右滑动时指示器上下跳。
 */
@Composable
fun rememberShortestMediaAspectRatio(
    urls: List<String>,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Float {
    val (aspect, _) = rememberShortestMediaAspectRatioLoaded(urls, fallback, min, max)
    return aspect
}

@Composable
fun rememberShortestMediaAspectRatioLoaded(
    urls: List<String>,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Pair<Float, Boolean> {
    val allCached = urls.isNotEmpty() && urls.all { aspectRatioCache.get(it) != null }
    var aspect by remember(urls, min, max) {
        mutableFloatStateOf(
            if (allCached) {
                urls.mapNotNull { aspectRatioCache.get(it) }.maxOrNull()?.coerceIn(min, max) ?: fallback
            } else {
                fallback
            },
        )
    }
    var isReady by remember(urls) { mutableStateOf(urls.isEmpty() || allCached) }
    val context = LocalContext.current
    LaunchedEffect(urls, min, max) {
        if (urls.isEmpty()) {
            aspect = fallback
            isReady = true
            return@LaunchedEffect
        }
        val ratios = mutableListOf<Float>()
        var allResolved = true
        for (url in urls) {
            val cached = aspectRatioCache.get(url)
            if (cached != null) {
                ratios += cached
                continue
            }
            val (probed, hasDimensions) = probeImageAspectRatio(context, url, fallback)
            if (hasDimensions) {
                aspectRatioCache.put(url, probed)
                ratios += probed
            } else {
                allResolved = false
            }
        }
        if (ratios.isNotEmpty()) {
            aspect = ratios.maxOrNull()?.coerceIn(min, max) ?: fallback
        }
        isReady = allResolved && ratios.size == urls.size
    }
    return aspect to isReady
}

/**
 * 多图场景：取「同宽下最高」的比例，即 min(宽/高)。
 */
@Composable
fun rememberTallestMediaAspectRatioLoaded(
    urls: List<String>,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Pair<Float, Boolean> {
    val allCached = urls.isNotEmpty() && urls.all { aspectRatioCache.get(it) != null }
    var aspect by remember(urls, min, max) {
        mutableFloatStateOf(
            if (allCached) {
                urls.mapNotNull { aspectRatioCache.get(it) }.minOrNull()?.coerceIn(min, max) ?: fallback
            } else {
                fallback
            },
        )
    }
    var isReady by remember(urls) { mutableStateOf(urls.isEmpty() || allCached) }
    val context = LocalContext.current
    LaunchedEffect(urls, min, max) {
        if (urls.isEmpty()) {
            aspect = fallback
            isReady = true
            return@LaunchedEffect
        }
        val ratios = mutableListOf<Float>()
        var allResolved = true
        for (url in urls) {
            val cached = aspectRatioCache.get(url)
            if (cached != null) {
                ratios += cached
                continue
            }
            val (probed, hasDimensions) = probeImageAspectRatio(context, url, fallback)
            if (hasDimensions) {
                aspectRatioCache.put(url, probed)
                ratios += probed
            } else {
                allResolved = false
            }
        }
        if (ratios.isNotEmpty()) {
            aspect = ratios.minOrNull()?.coerceIn(min, max) ?: fallback
        }
        isReady = allResolved && ratios.size == urls.size
    }
    return aspect to isReady
}

@Composable
actual fun rememberPostMediaAspectRatioLoaded(
    media: PostMedia,
    fallback: Float,
    min: Float,
    max: Float,
): Pair<Float, Boolean> {
    val cacheKey = remember(media) {
        "${media.type}:${media.aspectRatioSourceUrl}"
    }
    val cachedRaw = aspectRatioCache.get(cacheKey)
    var aspect by remember(cacheKey, min, max) {
        mutableFloatStateOf(cachedRaw?.coerceIn(min, max) ?: fallback)
    }
    var isReady by remember(cacheKey) { mutableStateOf(cachedRaw != null) }
    val context = LocalContext.current
    LaunchedEffect(cacheKey, media, min, max) {
        val fromCache = aspectRatioCache.get(cacheKey)
        if (fromCache != null) {
            aspect = fromCache.coerceIn(min, max)
            isReady = true
            return@LaunchedEffect
        }
        val (probed, hasDimensions) = when (media.type) {
            PostMediaType.VIDEO -> probeVideoAspectRatio(context, media.playbackUrl ?: media.url, fallback)
            else -> probeImageAspectRatio(context, media.aspectRatioSourceUrl, fallback)
        }
        if (hasDimensions) {
            aspectRatioCache.put(cacheKey, probed)
            aspect = probed.coerceIn(min, max)
            isReady = true
        } else {
            // 视频探测失败（如无可用封面）：退回声明式兜底比例，
            // 不让整块媒体区一直卡在加载态高度。详见 PostMediaAspect.kt。
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

@Composable
actual fun rememberPostMediaListAspectRatioLoaded(
    mediaList: List<PostMedia>,
    pickShortest: Boolean,
    fallback: Float,
    min: Float,
    max: Float,
): Pair<Float, Boolean> {
    val cacheKeys = remember(mediaList) {
        mediaList.map { "${it.type}:${it.aspectRatioSourceUrl}" }
    }
    val allCached = cacheKeys.isNotEmpty() && cacheKeys.all { aspectRatioCache.get(it) != null }
    var aspect by remember(cacheKeys, pickShortest, min, max) {
        mutableFloatStateOf(
            if (allCached) {
                val values = cacheKeys.mapNotNull { aspectRatioCache.get(it) }
                (if (pickShortest) values.maxOrNull() else values.minOrNull())?.coerceIn(min, max) ?: fallback
            } else {
                fallback
            },
        )
    }
    var isReady by remember(cacheKeys) { mutableStateOf(cacheKeys.isEmpty() || allCached) }
    val context = LocalContext.current
    LaunchedEffect(cacheKeys, mediaList, pickShortest, min, max) {
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
            val cached = aspectRatioCache.get(cacheKey)
            if (cached != null) {
                ratios += cached
                return@forEachIndexed
            }
            val (probed, hasDimensions) = when (media.type) {
                PostMediaType.VIDEO -> probeVideoAspectRatio(context, media.playbackUrl ?: media.url, fallback)
                else -> probeImageAspectRatio(context, media.aspectRatioSourceUrl, fallback)
            }
            if (hasDimensions) {
                aspectRatioCache.put(cacheKey, probed)
                ratios += probed
            } else {
                // 视频探测失败只记声明式兜底：既不参与容器高度计算，也不拖累 isReady；
                // 图片探测失败才算真正「未解析」。详见 PostMediaAspect.kt。
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

private suspend fun probeImageAspectRatio(
    context: Context,
    url: String?,
    fallback: Float,
): Pair<Float, Boolean> = withContext(Dispatchers.IO) {
    if (url.isNullOrBlank()) return@withContext fallback to false
    val resolved = MediaUrlResolver.resolve(url) ?: url
    val result = context.imageLoader.execute(
        ImageRequest.Builder(context)
            .data(resolved)
            .size(Size.ORIGINAL)
            .build(),
    )
    val drawable = (result as? SuccessResult)?.drawable
    val w = drawable?.intrinsicWidth ?: 0
    val h = drawable?.intrinsicHeight ?: 0
    if (w > 0 && h > 0) {
        (w.toFloat() / h.toFloat()) to true
    } else {
        fallback to false
    }
}
