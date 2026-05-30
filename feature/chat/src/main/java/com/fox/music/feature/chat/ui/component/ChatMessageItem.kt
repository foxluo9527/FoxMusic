package com.fox.music.feature.chat.ui.component

import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.chat.ui.util.parseFileContentName
import com.fox.music.feature.chat.ui.util.resolveMediaPreviewType
import com.fox.music.feature.chat.ui.util.resolveMediaPreviewUrl
import com.fox.music.feature.chat.util.formatMessageDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatMessageItem(
    message: Message,
    isOutgoing: Boolean,
    avatarUrl: String?,
    avatarContentDescription: String?,
    onRetry: (String) -> Unit,
    onMediaClick: (ChatMessageMediaViewer) -> Unit = {},
    onFileClick: (Message) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Text(
            text = formatMessageDate(message.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
        )
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isOutgoing) {
                MessageAvatar(
                    imageUrl = avatarUrl,
                    contentDescription = avatarContentDescription,
                )
            }
            if (isOutgoing && message.status == MessageStatus.FAILED) {
                val localId = message.localId
                if (localId != null) {
                    IconButton(
                        onClick = { onRetry(localId) },
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.CenterVertically),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重试",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else if (message.status == MessageStatus.SENDING) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically),
                    strokeWidth = 2.dp,
                )
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            ) {
                Box(
                    modifier = Modifier
                        .alpha(if (message.status == MessageStatus.SENDING) 0.7f else 1f)
                        .background(
                            color = if (isOutgoing) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(10.dp),
                ) {
                    ChatMessageContent(
                        message = message,
                        onMediaClick = onMediaClick,
                        onFileClick = onFileClick,
                    )
                }
                if (isOutgoing && message.status == MessageStatus.FAILED) {
                    message.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
            if (isOutgoing) {
                MessageAvatar(
                    imageUrl = avatarUrl,
                    contentDescription = avatarContentDescription,
                )
            }
        }
    }
}

@Composable
private fun MessageAvatar(
    imageUrl: String?,
    contentDescription: String?,
) {
    CachedImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        placeholderIcon = Icons.Default.Person,
    )
}

@Composable
private fun ChatMessageContent(
    message: Message,
    onMediaClick: (ChatMessageMediaViewer) -> Unit,
    onFileClick: (Message) -> Unit,
) {
    when (val previewType = message.resolveMediaPreviewType()) {
        ChatMediaPreviewType.IMAGE -> ImageMessageBubble(
            message = message,
            onClick = onMediaClick,
        )
        ChatMediaPreviewType.VIDEO -> VideoMessageBubble(
            message = message,
            onClick = onMediaClick,
        )
        null -> when (message.type) {
            MessageType.AUDIO -> AudioMessageBubble(message)
            MessageType.FILE -> FileMessageBubble(
                message = message,
                onClick = onFileClick,
            )
            MessageType.MUSIC -> TextMessageBubble("[音乐] ${message.content}")
            MessageType.TEXT -> TextMessageBubble(message.content)
            MessageType.IMAGE -> ImageMessageBubble(message, onClick = onMediaClick)
        }
    }
}

@Composable
private fun ImageMessageBubble(
    message: Message,
    onClick: (ChatMessageMediaViewer) -> Unit,
) {
    val previewUrl = message.resolveMediaPreviewUrl()
    val fileName = message.localMediaFileName ?: parseFileContentName(message.content)
    var imageRatio by remember(previewUrl) { mutableStateOf(1f) }
    SubcomposeAsyncImage(
        model = previewUrl,
        contentDescription = "图片消息",
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .aspectRatio(imageRatio)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = previewUrl != null) {
                previewUrl?.let { url ->
                    onClick(
                        ChatMessageMediaViewer(
                            previewType = ChatMediaPreviewType.IMAGE,
                            mediaUrl = url,
                            fileName = fileName,
                        ),
                    )
                }
            },
        contentScale = ContentScale.Fit,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        },
        onSuccess = { success ->
            val width = success.result.drawable.intrinsicWidth
            val height = success.result.drawable.intrinsicHeight
            if (width > 0 && height > 0) {
                imageRatio = width.toFloat() / height.toFloat()
            }
        },
    )
}

@Composable
private fun VideoMessageBubble(
    message: Message,
    onClick: (ChatMessageMediaViewer) -> Unit,
) {
    val previewUrl = message.resolveMediaPreviewUrl()
    val fileName = message.localMediaFileName ?: parseFileContentName(message.content)
    val context = LocalContext.current
    val frameBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = previewUrl,
    ) {
        value = extractVideoFrameBitmap(context, previewUrl)
    }
    val frameModel: ImageRequest? = remember(previewUrl, context) {
        previewUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                // 兼容 Coil 扩展不可见场景，显式指定视频首帧参数
                .setParameter("coil#video_frame_millis", 0L)
                .crossfade(true)
                .build()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = previewUrl != null) {
                previewUrl?.let { url ->
                    onClick(
                        ChatMessageMediaViewer(
                            previewType = ChatMediaPreviewType.VIDEO,
                            mediaUrl = url,
                            fileName = fileName,
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap!!.asImageBitmap(),
                contentDescription = "视频消息",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        } else if (frameModel != null) {
            SubcomposeAsyncImage(
                model = frameModel,
                contentDescription = "视频消息",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放视频",
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.Center),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private suspend fun extractVideoFrameBitmap(
    context: android.content.Context,
    previewUrl: String?,
): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    if (previewUrl.isNullOrBlank()) return@withContext null
    val uri = Uri.parse(previewUrl)
    val localThumb = if (uri.scheme.equals("content", ignoreCase = true)) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(720, 720), null)
        }.getOrNull()
    } else {
        null
    }
    if (localThumb != null) return@withContext localThumb

    runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.use { mmr ->
            when {
                uri.scheme.equals("content", ignoreCase = true) ||
                    uri.scheme.equals("file", ignoreCase = true) -> mmr.setDataSource(context, uri)
                previewUrl.startsWith("http://", ignoreCase = true) ||
                    previewUrl.startsWith("https://", ignoreCase = true) -> mmr.setDataSource(
                    previewUrl,
                    emptyMap(),
                )
                else -> mmr.setDataSource(previewUrl)
            }
            mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: mmr.frameAtTime
        }
    }.getOrNull()
}

@Composable
private fun TextMessageBubble(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AudioMessageBubble(message: Message) {
    val context = LocalContext.current
    var playing by remember(message.localId) { mutableStateOf(false) }
    val player = remember(message.localId) { MediaPlayer() }

    DisposableEffect(message.localId) {
        onDispose {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable {
            val rawUrl = message.localMediaUri?.takeIf { it.isNotBlank() }
                ?: message.remoteMediaUrl?.takeIf { it.isNotBlank() }
                ?: message.content
            val resolvedUrl = MediaUrlResolver.resolve(rawUrl) ?: return@clickable

            if (playing) {
                runCatching {
                    if (player.isPlaying) player.pause()
                    player.seekTo(0)
                }
                playing = false
                return@clickable
            }

            runCatching {
                player.reset()
                player.setOnCompletionListener { playing = false }
                player.setOnErrorListener { _, _, _ ->
                    playing = false
                    true
                }
                player.setOnPreparedListener { mp ->
                    mp.start()
                    playing = true
                }
                when {
                    resolvedUrl.startsWith("http://", ignoreCase = true) ||
                        resolvedUrl.startsWith("https://", ignoreCase = true) -> {
                        player.setDataSource(resolvedUrl)
                        player.prepareAsync()
                    }
                    else -> {
                        player.setDataSource(context, Uri.parse(resolvedUrl))
                        player.prepareAsync()
                    }
                }
            }.onFailure {
                playing = false
            }
        },
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = "播放语音")
        val durationSec = ((message.audioDurationMs ?: 0L) / 1000L).coerceAtLeast(1L)
        Text(text = "${durationSec}\"")
    }
}

@Composable
private fun FileMessageBubble(
    message: Message,
    onClick: (Message) -> Unit,
) {
    val fileName = message.localMediaFileName ?: parseFileContentName(message.content)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick(message) },
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
        )
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
