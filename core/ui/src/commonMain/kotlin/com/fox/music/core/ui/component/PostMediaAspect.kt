package com.fox.music.core.ui.component

import androidx.compose.runtime.Composable
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType

/**
 * 「视频没有可用封面」时用于排版的声明式兜底比例（16:9 横屏）。
 *
 * 为什么需要：视频封面探测依赖图片解码器（iOS 是 Skia），而 mp4 解码必然失败；
 * 后端又曾把视频文件本身回填进 cover_url（见 FoxResourceGroup services/postService.js:28）。
 * 若把这类 item 当成「永远探测未就绪」，[com.fox.music.core.ui.component.PostMediaCarousel]
 * 的整块媒体区会一直停在加载态兜底高度（240dp），连同一轮播里的图片也被压小。
 */
const val VIDEO_NO_COVER_FALLBACK_ASPECT: Float = 16f / 9f

/**
 * 视频的声明式兜底比例（16:9 横屏）；非视频返回 null。
 *
 * 用于「探测失败」的兜底路径：视频封面要么没有、要么可能下载/解码失败，
 * 无论哪种都不该让整块媒体区停在加载态高度。
 */
fun PostMedia.videoDeclaredAspectOrNull(): Float? =
    if (type == PostMediaType.VIDEO) VIDEO_NO_COVER_FALLBACK_ASPECT else null

/**
 * 无可用封面的视频 → 声明式兜底比例；其余（有封面 / 非视频）→ null。
 *
 * 注意 [PostMedia.imageCoverUrl] 已过滤掉「被误填成视频文件的 cover_url」，
 * 所以这里判空等价于「后端没给封面，或给的封面是视频文件」。
 * 这类 item 的探测注定失败，可直接跳过，省掉一次白下载。
 */
fun PostMedia.noCoverFallbackAspectOrNull(): Float? =
    videoDeclaredAspectOrNull()?.takeIf { imageCoverUrl == null }

/**
 * 广场/信息流封面宽高比约束（宽/高）。
 *
 * 比例常量与 [clampFeedMediaAspectRatio] 是纯计算，直接放 commonMain；
 * 真正「按 URL 探测真实宽高比」依赖平台解码能力（Android 用 Coil 拿 drawable 尺寸，
 * iOS 用 Ktor 下载 + Skia 解码），因此拆成 expect/actual。
 */
object PostFeedMediaAspectRatio {
    /** 瀑布流竖图上限（再高则裁切）：9:16 */
    const val MIN = 9f / 16f
    /** 单列列表竖图上限（再高则裁切）：2:3 */
    const val LIST_MIN = 2f / 3f
    /** 横图上限（再宽则裁切）：16:9 */
    const val MAX = 16f / 9f
}

fun clampFeedMediaAspectRatio(
    aspect: Float,
    min: Float = PostFeedMediaAspectRatio.MIN,
    max: Float = PostFeedMediaAspectRatio.MAX,
): Float = aspect.coerceIn(min, max)

/** 单个媒体的真实宽高比（宽/高）+ 是否已探测完成。 */
@Composable
expect fun rememberPostMediaAspectRatioLoaded(
    media: PostMedia,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Pair<Float, Boolean>

/** 多图场景：pickShortest=true 取「同宽下最矮」的比例，否则取最高的。 */
@Composable
expect fun rememberPostMediaListAspectRatioLoaded(
    mediaList: List<PostMedia>,
    pickShortest: Boolean,
    fallback: Float = 1f,
    min: Float = 0.45f,
    max: Float = 2.2f,
): Pair<Float, Boolean>
