package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fox.music.core.common.util.MediaUrlResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostImagePreviewDialog(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
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
                    val resolved = MediaUrlResolver.resolve(images[page]) ?: images[page]
                    ZoomablePreviewImage(
                        imageUrl = resolved,
                        contentDescription = "帖子图片",
                        onZoomedChange = { zoomed -> pagerScrollEnabled = !zoomed },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * 可双指缩放的图片预览内容。
 *
 * 图片加载走 [PlatformAsyncImage]（Android=Coil，iOS=Ktor+Skia），
 * 因此本文件整体留在 commonMain —— 之前用的 coil 的 SubcomposeAsyncImage 是 JVM-only。
 */
@Composable
internal fun ZoomablePreviewImage(
    imageUrl: String,
    contentDescription: String,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
            onError = {},
            loadingContent = {},
            errorContent = {},
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
