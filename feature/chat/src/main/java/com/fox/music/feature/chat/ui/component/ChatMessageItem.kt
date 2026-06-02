package com.fox.music.feature.chat.ui.component

import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import kotlin.math.roundToInt

@Composable
fun ChatMessageItem(
    message: Message,
    isOutgoing: Boolean,
    avatarUrl: String?,
    avatarContentDescription: String?,
    onRetry: (String) -> Unit,
    onMediaClick: (ChatMessageMediaViewer) -> Unit = {},
    onFileClick: (Message) -> Unit = {},
    onShareClick: (Message) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onRecall: (Long, String) -> Unit = { _, _ -> },
    onCancelSending: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onForward: (Message) -> Unit = {},
    onMultiSelect: (String) -> Unit = {},
    isInSelectionMode: Boolean = false,
    showTime: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember(message.localId) { mutableStateOf(false) }
    var bubbleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        ) {
            if (showTime) {
                Text(
                    text = formatMessageDate(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
            }
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
                            .onGloballyPositioned { coordinates ->
                                bubbleCoordinates = coordinates
                            }
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
                            onMediaClick = if (isInSelectionMode) { _ -> } else onMediaClick,
                            onFileClick = if (isInSelectionMode) { _ -> } else onFileClick,
                            onShareClick = if (isInSelectionMode) { _ -> } else onShareClick,
                            onLongClick = if (isInSelectionMode) ({ }) else ({ showContextMenu = true }),
                            onBubbleClick = if (isInSelectionMode && message.localId != null) {
                                { onMultiSelect(message.localId!!) }
                            } else {
                                { }
                            },
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
            Spacer(Modifier.height(10.dp))
        }

        if (showContextMenu) {
            val bubble = bubbleCoordinates
            if (bubble != null) {
                val bubblePosInWindow = bubble.positionInWindow()
                val bubbleSize = bubble.size

                val bubbleBottomInWindow = bubblePosInWindow.y + bubbleSize.height
                val bubbleTopInWindow = bubblePosInWindow.y

                // 该菜单是横向一行，实际高度稳定，用估算值决定上/下展示
                val estimatedPopupHeight = with(density) { 56.dp.toPx() }
                val showAbove = bubbleTopInWindow > estimatedPopupHeight

                val bubbleCenterXInWindow = bubblePosInWindow.x + bubbleSize.width / 2f

                MessageContextMenuPopup(
                    message = message,
                    showAbove = showAbove,
                    bubbleCenterXInWindow = bubbleCenterXInWindow,
                    bubbleTopYInWindow = bubbleTopInWindow,
                    bubbleBottomYInWindow = bubbleBottomInWindow,
                    onDismiss = { showContextMenu = false },
                    onDelete = { localId ->
                        showContextMenu = false
                        onDelete(localId)
                    },
                    onRecall = { messageId, localId ->
                        showContextMenu = false
                        onRecall(messageId, localId)
                    },
                    onCancelSending = { localId ->
                        showContextMenu = false
                        onCancelSending(localId)
                    },
                    onCopy = { content ->
                        showContextMenu = false
                        onCopy(content)
                    },
                    onForward = { msg ->
                        showContextMenu = false
                        onForward(msg)
                    },
                    onMultiSelect = { localId ->
                        showContextMenu = false
                        onMultiSelect(localId)
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageContextMenuPopup(
    message: Message,
    showAbove: Boolean,
    bubbleCenterXInWindow: Float,
    bubbleTopYInWindow: Float,
    bubbleBottomYInWindow: Float,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onRecall: (Long, String) -> Unit,
    onCancelSending: (String) -> Unit,
    onCopy: (String) -> Unit,
    onForward: (Message) -> Unit,
    onMultiSelect: (String) -> Unit,
) {
    val localId = message.localId ?: return
    val hasServerId = message.id > 0L && message.status != MessageStatus.SENDING && message.status != MessageStatus.FAILED
    val canRecall = hasServerId && !message.isRecalled
    val isPlainText = message.type == MessageType.TEXT
    val canForward = message.type != MessageType.AUDIO && message.status != MessageStatus.FAILED

    val density = LocalDensity.current
    val triangleHeightPx = with(density) { 10.dp.toPx() }
    val triangleWidthPx = with(density) { 20.dp.toPx() }
    val edgePaddingPx = with(density) { 8.dp.toPx() }
    var triangleOffsetXPx by remember {
        mutableStateOf(0f)
    }
    val positionProvider = remember(
        showAbove,
        bubbleCenterXInWindow,
        bubbleTopYInWindow,
        bubbleBottomYInWindow,
        triangleWidthPx,
        edgePaddingPx,
    ) {
        MessagePopupPositionProvider(
            showAbove = showAbove,
            bubbleCenterXInWindow = bubbleCenterXInWindow,
            bubbleTopYInWindow = bubbleTopYInWindow,
            bubbleBottomYInWindow = bubbleBottomYInWindow,
            triangleWidthPx = triangleWidthPx,
            edgePaddingPx = edgePaddingPx,
            onTriangleOffsetX = { triangleOffsetXPx = it },
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val triangleOffsetXDp = with(density) { triangleOffsetXPx.toDp() }
        Column(
            horizontalAlignment = Alignment.Start,
        ) {
            if (showAbove) {
                MessageContextMenuCard(
                    message = message,
                    localId = localId,
                    canRecall = canRecall,
                    isPlainText = isPlainText,
                    canForward = canForward,
                    onDelete = onDelete,
                    onRecall = onRecall,
                    onCancelSending = onCancelSending,
                    onCopy = onCopy,
                    onForward = onForward,
                    onMultiSelect = onMultiSelect,
                )
                TriangleDown(modifier = Modifier.offset(x = triangleOffsetXDp))
            } else {
                TriangleUp(modifier = Modifier.offset(x = triangleOffsetXDp))
                MessageContextMenuCard(
                    message = message,
                    localId = localId,
                    canRecall = canRecall,
                    isPlainText = isPlainText,
                    canForward = canForward,
                    onDelete = onDelete,
                    onRecall = onRecall,
                    onCancelSending = onCancelSending,
                    onCopy = onCopy,
                    onForward = onForward,
                    onMultiSelect = onMultiSelect,
                )
            }
        }
    }
}

/**
 * 自定义 Popup 位置提供器，用于精确定位消息菜单
 */
private class MessagePopupPositionProvider(
    private val showAbove: Boolean,
    private val bubbleCenterXInWindow: Float,
    private val bubbleTopYInWindow: Float,
    private val bubbleBottomYInWindow: Float,
    private val triangleWidthPx: Float,
    private val edgePaddingPx: Float,
    private val onTriangleOffsetX: (Float) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val popupWidth = popupContentSize.width
        val xRaw = bubbleCenterXInWindow - popupWidth / 2f
        val x = xRaw.coerceIn(edgePaddingPx, windowSize.width - popupWidth - edgePaddingPx)

        val y = if (showAbove) {
            // 整个菜单（含箭头）底部贴齐气泡顶部
            bubbleTopYInWindow - popupContentSize.height
        } else {
            // 整个菜单（含箭头）顶部贴齐气泡底部
            bubbleBottomYInWindow
        }

        val triangleX = (bubbleCenterXInWindow - x - triangleWidthPx / 2f)
            .coerceIn(edgePaddingPx, popupWidth - triangleWidthPx - edgePaddingPx)
        onTriangleOffsetX(triangleX)

        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

@Composable
private fun MessageContextMenuCard(
    message: Message,
    localId: String,
    canRecall: Boolean,
    isPlainText: Boolean,
    canForward: Boolean,
    onDelete: (String) -> Unit,
    onRecall: (Long, String) -> Unit,
    onCancelSending: (String) -> Unit,
    onCopy: (String) -> Unit,
    onForward: (Message) -> Unit,
    onMultiSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (message.status == MessageStatus.SENDING) {
                ContextMenuItem("取消发送") { onCancelSending(localId) }
            } else {
                if (isPlainText) {
                    ContextMenuItem("复制") { onCopy(message.content) }
                    ContextMenuDivider()
                }
                if (canForward) {
                    ContextMenuItem("转发") { onForward(message) }
                    ContextMenuDivider()
                }
                if (canRecall) {
                    ContextMenuItem("撤回") { onRecall(message.id, localId) }
                    ContextMenuDivider()
                }
                ContextMenuItem("删除") { onDelete(localId) }
                ContextMenuDivider()
                ContextMenuItem("多选") { onMultiSelect(localId) }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ContextMenuDivider() {
    Text(
        text = "丨",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun TriangleUp(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier
            .width(20.dp)
            .height(10.dp)
    ) {
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width, size.height)
            lineTo(size.width / 2f, 0f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
private fun TriangleDown(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier
            .width(20.dp)
            .height(10.dp)
    ) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, color = color)
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
    onShareClick: (Message) -> Unit = {},
    onLongClick: () -> Unit = {},
    onBubbleClick: () -> Unit = {},
) {
    when (val previewType = message.resolveMediaPreviewType()) {
        ChatMediaPreviewType.IMAGE -> ImageMessageBubble(
            message = message,
            onClick = onMediaClick,
            onLongClick = onLongClick,
            onBubbleClick = onBubbleClick,
        )
        ChatMediaPreviewType.VIDEO -> VideoMessageBubble(
            message = message,
            onClick = onMediaClick,
            onLongClick = onLongClick,
            onBubbleClick = onBubbleClick,
        )
        null -> when (message.type) {
            MessageType.AUDIO -> AudioMessageBubble(message, onLongClick = onLongClick, onBubbleClick = onBubbleClick)
            MessageType.FILE -> FileMessageBubble(
                message = message,
                onClick = onFileClick,
                onLongClick = onLongClick,
                onBubbleClick = onBubbleClick,
            )
            MessageType.MUSIC -> TextMessageBubble("[音乐] ${message.content}", onLongClick = onLongClick, onBubbleClick = onBubbleClick)
            MessageType.SHARE -> ShareMessageBubble(
                message = message,
                onClick = { onShareClick(message) },
                onLongClick = onLongClick,
                onBubbleClick = onBubbleClick,
            )
            MessageType.TEXT -> TextMessageBubble(message.content, onLongClick = onLongClick, onBubbleClick = onBubbleClick)
            MessageType.IMAGE -> ImageMessageBubble(message, onClick = onMediaClick, onLongClick = onLongClick, onBubbleClick = onBubbleClick)
        }
    }
}

@Composable
private fun ImageMessageBubble(
    message: Message,
    onClick: (ChatMessageMediaViewer) -> Unit,
    onLongClick: () -> Unit = {},
    onBubbleClick: () -> Unit = {},
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
            .combinedClickable(
                enabled = previewUrl != null,
                onClick = {
                    onBubbleClick()
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
                onLongClick = onLongClick,
            ),
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
    onLongClick: () -> Unit = {},
    onBubbleClick: () -> Unit = {},
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
            .combinedClickable(
                enabled = previewUrl != null,
                onClick = {
                    onBubbleClick()
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
                onLongClick = onLongClick,
            ),
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
private fun TextMessageBubble(text: String, onLongClick: () -> Unit = {}, onBubbleClick: () -> Unit = {}) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.combinedClickable(
            onClick = onBubbleClick,
            onLongClick = onLongClick,
        ),
    )
}

@Composable
private fun AudioMessageBubble(message: Message, onLongClick: () -> Unit = {}, onBubbleClick: () -> Unit = {}) {
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
        modifier = Modifier.combinedClickable(
            onClick = {
                onBubbleClick()
                val rawUrl = message.localMediaUri?.takeIf { it.isNotBlank() }
                    ?: message.remoteMediaUrl?.takeIf { it.isNotBlank() }
                    ?: message.content
                val resolvedUrl = MediaUrlResolver.resolve(rawUrl) ?: return@combinedClickable

                if (playing) {
                    runCatching {
                        if (player.isPlaying) player.pause()
                        player.seekTo(0)
                    }
                    playing = false
                    return@combinedClickable
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
            onLongClick = onLongClick,
        ),
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
    onLongClick: () -> Unit = {},
    onBubbleClick: () -> Unit = {},
) {
    val fileName = message.localMediaFileName ?: parseFileContentName(message.content)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.combinedClickable(
            onClick = { onBubbleClick(); onClick(message) },
            onLongClick = onLongClick,
        ),
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

@Composable
private fun ShareMessageBubble(
    message: Message,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBubbleClick: () -> Unit = {},
) {
    val shareData = message.shareData
    val shareType = message.shareType ?: "music"
    val typeLabel = when (shareType) {
        "music" -> "🎵 音乐"
        "playlist" -> "📋 歌单"
        "artist" -> "🎤 艺人"
        "album" -> "💿 专辑"
        else -> "🔗 分享"
    }
    val hasData = shareData != null

    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = { onBubbleClick(); if (hasData) onClick() },
                onLongClick = onLongClick,
            )
            .widthIn(max = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasData) {
            val coverUrl = shareData.coverImage ?: shareData.avatar
            if (coverUrl != null) {
                CachedImage(
                    imageUrl = coverUrl,
                    contentDescription = shareData.title ?: shareData.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    shape = RoundedCornerShape(6.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = typeLabel.take(2), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasData) {
                Text(
                    text = shareData.title ?: shareData.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (shareType) {
                    "music" -> shareData.artists.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.name }
                    "playlist" -> shareData.description?.takeIf { it.isNotBlank() }
                    "artist" -> shareData.description?.takeIf { it.isNotBlank() }
                    "album" -> shareData.artists.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.name }
                    else -> null
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = "该内容已删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}
