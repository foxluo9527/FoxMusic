package com.fox.music.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType

/**
 * Live 徽章：Android 用矢量 drawable（ic_post_live_badge），
 * iOS 用同一份视觉来源的 Compose 矢量（FoxLiveBadgeVector），两端对齐。
 * 拆 expect/actual（实现分别在 androidMain / iosMain 的 PostVideo* 文件）。
 */
@Composable
expect fun PostLiveBadge(modifier: Modifier)

/**
 * 视频封面：Android 用 MediaMetadataRetriever 抽帧（无封面图时），
 * iOS 只展示 cover_url 兜底（K/N 的 AVFoundation 绑定残缺，见 KMP 迁移备忘）。
 */
@Composable
expect fun PostVideoCover(
    media: PostMedia,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showPlayIcon: Boolean = true,
)

/**
 * Live 预览顶栏徽章：未播放时显示斜线，播放时斜线淡出；点击可重新播放。
 */
@Composable
fun PostLivePreviewBadge(
    showSlash: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slashAlpha by animateFloatAsState(
        targetValue = if (showSlash) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "liveSlashAlpha",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PostLiveBadge(modifier = Modifier.size(24.dp))
        if (slashAlpha > 0.01f) {
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(slashAlpha),
            ) {
                val strokeWidth = (size.minDimension * 0.1f).coerceAtLeast(2f)
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.18f, size.height * 0.82f),
                    end = Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun PostMediaCarousel(
    media: List<PostMedia>,
    onMediaClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    /**
     * 固定容器宽高比。null 时按所有媒体中「同宽最高」的比例（min 宽/高），
     * 最多不超过 [maxHeight]；较矮的 item 在容器内竖向居中。
     */
    containerAspectRatio: Float? = null,
    /** 限制媒体区域最大高度；pager 始终铺满宽度，单图超高时高度顶满、宽度按比例缩小并居中 */
    maxHeight: Dp? = null,
    /** 探测到的宽高比下限（宽/高），默认允许较极端比例 */
    minAspectRatio: Float = 0.45f,
    /** 探测到的宽高比上限（宽/高） */
    maxAspectRatio: Float = 2.2f,
    /** 超出 [minAspectRatio]/[maxAspectRatio] 的容器内裁切展示（广场信息流） */
    cropOverflow: Boolean = false,
) {
    if (media.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { media.size })
    val mediaShape: Shape = if (cornerRadius > 0.dp) {
        RoundedCornerShape(cornerRadius)
    } else {
        RectangleShape
    }
    val (measuredAspect, aspectReady) = if (maxHeight != null) {
        rememberPostMediaListAspectRatioLoaded(
            mediaList = media,
            pickShortest = false,
            fallback = 1f,
            min = minAspectRatio,
            max = maxAspectRatio,
        )
    } else {
        rememberPostMediaListAspectRatioLoaded(
            mediaList = media,
            pickShortest = true,
            fallback = 1f,
            min = minAspectRatio,
            max = maxAspectRatio,
        )
    }
    val pagerAspect = containerAspectRatio ?: measuredAspect

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val safeAspect = pagerAspect.coerceAtLeast(0.01f)
        val heightAtFullWidth = maxWidth / safeAspect
        val outerHeight = when {
            maxHeight != null && (containerAspectRatio != null || aspectReady) -> {
                minOf(heightAtFullWidth, maxHeight)
            }
            maxHeight != null -> {
                minOf(240.dp, maxHeight)
            }
            containerAspectRatio != null || aspectReady -> {
                heightAtFullWidth
            }
            else -> {
                240.dp
            }
        }
        val containerWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(outerHeight),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(outerHeight)
                    .clip(mediaShape),
            ) { page ->
                PostMediaCarouselPage(
                    item = media[page],
                    page = page,
                    containerWidth = containerWidth,
                    containerHeight = outerHeight,
                    onMediaClick = onMediaClick,
                    minAspectRatio = minAspectRatio,
                    maxAspectRatio = maxAspectRatio,
                    cropOverflow = cropOverflow,
                )
            }
        }

        if (media.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(media.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color(0xFFE53935)
                                else Color.White.copy(alpha = 0.7f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostMediaCarouselPage(
    item: PostMedia,
    page: Int,
    containerWidth: Dp,
    containerHeight: Dp,
    onMediaClick: (Int) -> Unit,
    minAspectRatio: Float = 0.45f,
    maxAspectRatio: Float = 2.2f,
    cropOverflow: Boolean = false,
) {
    val (itemAspect, aspectReady) = rememberPostMediaAspectRatioLoaded(
        media = item,
        fallback = if (item.type == PostMediaType.VIDEO) VIDEO_NO_COVER_FALLBACK_ASPECT else 1f,
        min = minAspectRatio,
        max = maxAspectRatio,
    )
    val contentScale = when {
        cropOverflow && aspectReady -> ContentScale.Crop
        aspectReady -> ContentScale.Fit
        else -> ContentScale.Crop
    }
    val mediaModifier = if (cropOverflow) {
        Modifier.fillMaxSize()
    } else {
        // 未就绪（还在探测 / 探测失败）也按兜底比例排一个合理盒子，而不是铺满容器 ——
        // 否则一个加载不出来的 item 会把整块媒体区撑成它自己的高度（视频无封面时尤甚）。
        val safeAspect = itemAspect.coerceAtLeast(0.01f)
        val itemHeightAtContainerWidth = containerWidth / safeAspect
        val itemWidthAtContainerHeight = containerHeight * safeAspect
        if (itemHeightAtContainerWidth > containerHeight) {
            Modifier
                .width(itemWidthAtContainerHeight)
                .height(containerHeight)
        } else {
            Modifier
                .fillMaxWidth()
                .height(itemHeightAtContainerWidth)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onMediaClick(page) },
        contentAlignment = Alignment.Center,
    ) {
        when (item.type) {
            PostMediaType.IMAGE -> {
                CachedImage(
                    imageUrl = item.url,
                    contentDescription = "帖子图片",
                    modifier = mediaModifier,
                    shape = RectangleShape,
                    contentScale = contentScale,
                )
            }
            PostMediaType.VIDEO -> {
                PostVideoPage(
                    item = item,
                    onOpen = { onMediaClick(page) },
                    mediaModifier = mediaModifier,
                    contentScale = contentScale,
                )
            }
            PostMediaType.LIVE -> {
                Box(modifier = mediaModifier) {
                    CachedImage(
                        imageUrl = item.url,
                        contentDescription = "Live 图",
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        contentScale = contentScale,
                    )
                    PostLiveBadge(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostVideoPage(
    item: PostMedia,
    onOpen: () -> Unit,
    mediaModifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = mediaModifier.clickable(onClick = onOpen),
    ) {
        PostVideoCover(
            media = item,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            showPlayIcon = true,
        )
    }
}

/** ContentScale.Fit：在容器内完整显示 */
internal fun fitInside(maxWidth: Dp, maxHeight: Dp, aspect: Float): DpSize {
    val safe = aspect.coerceAtLeast(0.01f)
    return if (maxWidth / maxHeight > safe) {
        val height = maxHeight
        DpSize(width = height * safe, height = height)
    } else {
        val width = maxWidth
        DpSize(width = width, height = width / safe)
    }
}

internal data class DpSize(val width: Dp, val height: Dp)

/** 视频封面上的播放按钮（两端共用的纯 Compose 实现）。 */
@Composable
internal fun PostVideoPlayIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "播放视频",
        tint = Color.White,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(8.dp),
    )
}
