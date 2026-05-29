package com.fox.music.feature.chat.ui.component

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.chat.util.formatMessageDate

@Composable
fun ChatMessageItem(
    message: Message,
    isOutgoing: Boolean,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message.status == MessageStatus.FAILED) {
                val localId = message.localId
                if (localId != null) {
                    IconButton(
                        onClick = { onRetry(localId) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重试",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
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
                ChatMessageContent(message = message)
            }
            if (message.status == MessageStatus.SENDING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            if (message.status == MessageStatus.FAILED) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "发送失败",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = formatMessageDate(message.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChatMessageContent(message: Message) {
    when (message.type) {
        MessageType.IMAGE -> {
            val url = message.localMediaUri ?: MediaUrlResolver.resolve(message.content)
            CachedImage(
                imageUrl = url,
                contentDescription = "图片消息",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop,
            )
        }
        MessageType.AUDIO -> AudioMessageBubble(message)
        MessageType.FILE -> FileMessageBubble(message)
        MessageType.MUSIC -> TextMessageBubble("[音乐] ${message.content}")
        MessageType.TEXT -> TextMessageBubble(message.content)
    }
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
            val rawUrl = message.localMediaUri?.takeIf { it.isNotBlank() } ?: message.content
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
private fun FileMessageBubble(message: Message) {
    val fileName = message.localMediaFileName
        ?: message.content.substringAfterLast('/').ifBlank { "文件" }
    val isVideo = isVideoFile(fileName) || isVideoFile(message.content)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.AttachFile,
            contentDescription = null,
        )
        Text(
            text = if (isVideo) "[视频] $fileName" else fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun isVideoFile(value: String): Boolean {
    val lower = value.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
        lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm")
}
