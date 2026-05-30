package com.fox.music.feature.chat.ui.component

import android.net.Uri
import android.util.Size
import android.widget.VideoView
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.fox.music.feature.chat.ui.util.formatMediaSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ChatMediaPreviewType {
    IMAGE,
    VIDEO,
}

data class ChatPendingMedia(
    val uri: Uri,
    val type: ChatMediaPreviewType,
    val fileName: String? = null,
    val sendOriginal: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMediaPreviewDialog(
    media: ChatPendingMedia,
    onDismiss: () -> Unit,
    onSend: (ChatPendingMedia) -> Unit,
    onCrop: (Uri) -> Unit,
    onSendOriginalChange: (Boolean) -> Unit,
    isProcessing: Boolean = false,
    processingText: String = "处理中...",
) {
    var isMuted by remember(media.uri) { mutableStateOf(false) }
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
                            text = media.fileName?.takeIf { it.isNotBlank() } ?: "文件",
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.White)
                        }
                    },
                    actions = {
                        if (media.type == ChatMediaPreviewType.VIDEO) {
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
                    when (media.type) {
                        ChatMediaPreviewType.IMAGE -> {
                            ZoomableAsyncImage(
                                model = media.uri,
                                contentDescription = "图片预览",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        ChatMediaPreviewType.VIDEO -> {
                            VideoPlayablePreview(
                                uri = media.uri,
                                isMuted = isMuted,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (isProcessing) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = processingText,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val context = LocalContext.current
                    val sizeText = remember(media.uri) { formatMediaSize(context, media.uri) }
                    if (sizeText != null) {
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = if (media.type == ChatMediaPreviewType.IMAGE) "发送原图" else "发送原视频",
                                color = Color.White,
                            )
                            Text(
                                text = if (media.type == ChatMediaPreviewType.IMAGE) {
                                    "关闭后将压缩图片以节省流量"
                                } else {
                                    "关闭后将压缩视频以节省流量"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                        Switch(
                            checked = media.sendOriginal,
                            onCheckedChange = onSendOriginalChange,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (media.type == ChatMediaPreviewType.IMAGE) {
                            OutlinedButton(
                                onClick = { onCrop(media.uri) },
                                enabled = !isProcessing,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null)
                                Text(text = " 裁剪", color = Color.White)
                            }
                        }
                        Button(
                            onClick = { onSend(media) },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Text(text = " 发送")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayablePreview(
    uri: Uri,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPrepared by remember(uri) { mutableStateOf(false) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var isBuffering by remember(uri) { mutableStateOf(false) }
    var hasError by remember(uri) { mutableStateOf(false) }
    var videoViewRef by remember(uri) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(uri) { mutableStateOf<android.media.MediaPlayer?>(null) }
    val latestMuted by rememberUpdatedState(isMuted)
    val thumbnailBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = extractVideoFrameBitmap(context, uri, Size(1280, 720))
    }
    val model = ImageRequest.Builder(context)
        .data(uri)
        .setParameter("coil#video_frame_millis", 0L)
        .crossfade(true)
        .build()
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    videoViewRef = this
                    setVideoURI(uri)
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

        if (!isPrepared) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                    contentDescription = "视频预览占位图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = "视频预览占位图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (isBuffering || (!isPrepared && !hasError)) {
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
    model: Any,
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

private suspend fun extractVideoFrameBitmap(
    context: android.content.Context,
    uri: Uri,
    thumbSize: Size,
): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.loadThumbnail(uri, thumbSize, null)
    }.getOrNull() ?: runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}
