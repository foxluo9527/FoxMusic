package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fox.music.core.common.util.MediaUrlResolver

/**
 * 全屏图片预览。
 *
 * @param fallbackImages 兜底图链：当前页主图加载失败时按顺序依次尝试。
 *   用于「头像 avatar 已失效、封面 coverImage 仍可用」这类数据——列表里
 *   [CachedImage] 有 errorImageUrl 兜底所以看不出异常，长按预览若只传
 *   avatar 就是一片纯黑（errorContent 原是空的）。传了兜底链后与列表语义对齐。
 *   多图帖子用不到，传空即可（默认行为不变）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostImagePreviewDialog(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    fallbackImages: List<String> = emptyList(),
) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    var pagerScrollEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage) {
        pagerScrollEnabled = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${pagerState.currentPage + 1}/${images.size}",
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        scrolledContainerColor = Color.Black,
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerScrollEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                ) { page ->
                    // 主图 + 兜底链按序尝试；remember 住避免每次重组都重建
                    val candidates = remember(images[page], fallbackImages) {
                        (listOfNotNull(images[page]) + fallbackImages)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                    }
                    var candidateIndex by remember(images[page], fallbackImages) {
                        mutableIntStateOf(0)
                    }
                    if (candidates.isEmpty()) {
                        PreviewImageError()
                    } else {
                        val safeIndex = candidateIndex.coerceIn(0, candidates.lastIndex)
                        val resolved = MediaUrlResolver.resolve(candidates[safeIndex]) ?: candidates[safeIndex]
                        ZoomablePreviewImage(
                            imageUrl = resolved,
                            contentDescription = "帖子图片",
                            onZoomedChange = { zoomed -> pagerScrollEnabled = !zoomed },
                            modifier = Modifier.fillMaxSize(),
                            onError = {
                                // 还有兜底图就换下一张，全挂掉则进入错误态
                                if (safeIndex < candidates.lastIndex) {
                                    candidateIndex = safeIndex + 1
                                }
                            },
                            errorContent = { PreviewImageError() },
                        )
                    }
                }
            }
        }
    }
}

/** 预览图全部加载失败时的占位：原来是纯黑（errorContent 传空），用户完全看不出所以然。 */
@Composable
private fun PreviewImageError(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "图片加载失败",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 可双指缩放的图片预览内容。
 *
 * 图片加载走 [PlatformAsyncImage]（Android=Coil，iOS=Ktor+Skia），
 * 因此本文件整体留在 commonMain —— 之前用的 coil 的 SubcomposeAsyncImage 是 JVM-only。
 *
 * @param onError 加载失败回调（调用方可在还有兜底图时切下一张）
 * @param errorContent 失败占位；不传则保持空白（黑底）
 */
@Composable
internal fun ZoomablePreviewImage(
    imageUrl: String,
    contentDescription: String,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
    errorContent: @Composable () -> Unit = {},
) {
    ZoomablePreviewContainer(
        resetKey = imageUrl,
        enabled = true,
        onZoomedChange = onZoomedChange,
        modifier = modifier,
    ) { contentModifier ->
        PlatformAsyncImage(
            url = imageUrl,
            contentDescription = contentDescription,
            modifier = contentModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onError = onError,
            loadingContent = {},
            errorContent = { errorContent() },
        )
    }
}

@Composable
internal fun ZoomablePreviewContainer(
    resetKey: Any?,
    enabled: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    val currentScale by rememberUpdatedState(scale)

    LaunchedEffect(resetKey) {
        scale = 1f
        offset = Offset.Zero
        onZoomedChange(false)
    }

    LaunchedEffect(scale, enabled) {
        onZoomedChange(enabled && scale > 1.01f)
    }

    val contentModifier = Modifier.graphicsLayer(
        scaleX = if (enabled) scale else 1f,
        scaleY = if (enabled) scale else 1f,
        translationX = if (enabled) offset.x else 0f,
        translationY = if (enabled) offset.y else 0f,
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(resetKey) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (currentScale > 1.01f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        }
                        .pointerInput(resetKey) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedPointerCount = event.changes.count { it.pressed }
                                    val shouldHandle = pressedPointerCount >= 2 || currentScale > 1.01f
                                    if (shouldHandle) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val newScale = (currentScale * zoomChange).coerceIn(1f, 5f)
                                        scale = newScale
                                        offset = if (newScale > 1f) {
                                            offset + panChange
                                        } else {
                                            Offset.Zero
                                        }
                                        event.changes.forEach { change ->
                                            if (change.pressed) {
                                                change.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(contentModifier)
    }
}
