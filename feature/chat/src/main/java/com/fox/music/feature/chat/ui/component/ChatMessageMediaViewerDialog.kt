package com.fox.music.feature.chat.ui.component

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.fox.music.core.common.util.MediaUrlResolver

data class ChatMessageMediaViewer(
    val previewType: ChatMediaPreviewType,
    val mediaUrl: String,
    val fileName: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageMediaViewerDialog(
    viewer: ChatMessageMediaViewer,
    onDismiss: () -> Unit,
) {
    var isMuted by remember(viewer.mediaUrl) { mutableStateOf(false) }
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
                            text = viewer.fileName?.takeIf { it.isNotBlank() } ?: "文件",
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                        }
                    },
                    actions = {
                        if (viewer.previewType == ChatMediaPreviewType.VIDEO) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isMuted) "取消静音" else "静音",
                                    tint = Color.White,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    when (viewer.previewType) {
                        ChatMediaPreviewType.IMAGE -> {
                            ZoomableAsyncImage(
                                model = MediaUrlResolver.resolve(viewer.mediaUrl),
                                contentDescription = viewer.fileName ?: "图片",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        ChatMediaPreviewType.VIDEO -> {
                            MessageVideoPreview(
                                mediaUrl = viewer.mediaUrl,
                                isMuted = isMuted,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MessageVideoPreview(
    mediaUrl: String,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
) {
    val resolved = MediaUrlResolver.resolve(mediaUrl) ?: mediaUrl
    var isPrepared by remember(resolved) { mutableStateOf(false) }
    var isPlaying by remember(resolved) { mutableStateOf(false) }
    var isBuffering by remember(resolved) { mutableStateOf(false) }
    var hasError by remember(resolved) { mutableStateOf(false) }
    var videoViewRef by remember(resolved) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(resolved) { mutableStateOf<android.media.MediaPlayer?>(null) }
    val latestMuted by rememberUpdatedState(isMuted)
    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                videoViewRef = this
                when {
                    resolved.startsWith("http://", ignoreCase = true) ||
                            resolved.startsWith("https://", ignoreCase = true) -> setVideoURI(Uri.parse(resolved))
                    else -> setVideoURI(Uri.parse(resolved))
                }
                setOnPreparedListener { player ->
                    mediaPlayerRef = player
                    isPrepared = true
                    hasError = false
                    isBuffering = false
                    player.isLooping = false
                    player.setVolume(
                        if (latestMuted) 0f else 1f,
                        if (latestMuted) 0f else 1f,
                    )
                    seekTo(1)
                    pause()
                    isPlaying = false
                }
                setOnCompletionListener {
                    isPlaying = false
                    seekTo(0)
                }
                setOnInfoListener { _, what, _ ->
                    when (what) {
                        android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                            isBuffering = true
                            true
                        }
                        android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END,
                        android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                            isBuffering = false
                            true
                        }
                        else -> false
                    }
                }
                setOnErrorListener { _, _, _ ->
                    hasError = true
                    isBuffering = false
                    isPlaying = false
                    true
                }
            }
        },
        update = {
            mediaPlayerRef?.setVolume(
                if (isMuted) 0f else 1f,
                if (isMuted) 0f else 1f,
            )
        },
        onRelease = { view ->
            view.stopPlayback()
            videoViewRef = null
            mediaPlayerRef = null
        },
    )
        if (isBuffering || !isPrepared) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (hasError) {
            Text(
                text = "视频加载失败",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (isPrepared && !hasError) {
            IconButton(
                onClick = {
                    val view = videoViewRef ?: return@IconButton
                    if (isPlaying) {
                        view.pause()
                        isPlaying = false
                    } else {
                        view.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier.align(Alignment.Center),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ZoomableAsyncImage(
    model: Any?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    if (nextScale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            contentScale = ContentScale.Fit,
        )
    }
}
