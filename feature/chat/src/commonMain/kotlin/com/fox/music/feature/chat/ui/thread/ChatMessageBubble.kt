package com.fox.music.feature.chat.ui.thread

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.chat.postBodyText
import com.fox.music.core.model.chat.postCoverImage
import com.fox.music.core.model.chat.userCoverImage
import com.fox.music.core.model.chat.userDisplayName
import com.fox.music.core.model.chat.userSignature
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.PostLiveBadge
import com.fox.music.feature.chat.ui.component.VoicePlayingIcon
import com.fox.music.feature.chat.ui.util.ChatMessageMediaViewer
import com.fox.music.feature.chat.ui.util.parseFileContentName
import com.fox.music.feature.chat.ui.util.resolveMediaPreviewType
import com.fox.music.feature.chat.ui.util.toChatMessageMediaViewer
import com.fox.music.feature.chat.util.formatMessageDate

/**
 * 单条消息气泡 —— 跨端实现（不依赖任何 Android API）。
 *
 * 与 Android 的 `ChatMessageItem` 的差异（刻意为之）：
 *  - 长按菜单用 Material3 `DropdownMenu`（Popup 定位由框架处理），
 *    而不是 Android 版那套基于 `positionInWindow` 的自绘横向菜单 —— 后者在 iOS 上
 *    拿不到稳定的窗口坐标；
 *  - 语音播放由宿主接管（`onPlayVoice` + `isPlayingVoice`），不在组件内建 MediaPlayer；
 *  - 视频/图片的预览也交回宿主（`onMediaClick`），组件只负责列表里的封面渲染。
 * 视觉（气泡底色、圆角、头像位置、状态图标）与 Android 版保持一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: Message,
    isOutgoing: Boolean,
    avatarUrl: String?,
    avatarContentDescription: String?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isPlayingVoice: Boolean,
    onAvatarClick: () -> Unit,
    onRetry: (String) -> Unit,
    onRecall: (Long, String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit,
    onForward: (Message) -> Unit,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onMediaClick: (ChatMessageMediaViewer) -> Unit,
    onShareClick: (Message) -> Unit,
    onPlayVoice: (Message) -> Unit,
    showTime: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember(message.localId) { mutableStateOf(false) }
    val localId = message.localId

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        if (showTime) {
            Text(
                text = formatMessageDate(message.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(10.dp))
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (isSelected) "已选中" else "未选中",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterVertically),
                )
            }

            if (!isOutgoing) {
                MessageAvatar(avatarUrl, avatarContentDescription, onAvatarClick)
            }

            if (isOutgoing && message.status == MessageStatus.FAILED) {
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isOutgoing) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .combinedClickable(
                            onLongClick = {
                                if (isSelectionMode) {
                                    localId?.let(onToggleSelection)
                                } else {
                                    showMenu = true
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    localId?.let(onToggleSelection)
                                } else {
                                    handleBubbleClick(
                                        message = message,
                                        onMediaClick = onMediaClick,
                                        onShareClick = onShareClick,
                                    )
                                }
                            },
                        )
                        .padding(10.dp),
                ) {
                    MessageContent(
                        message = message,
                        isPlayingVoice = isPlayingVoice,
                        onPlayVoice = onPlayVoice,
                        onMediaClick = onMediaClick,
                    )

                    // 长按动作菜单：Popup 定位交给框架，两端一致
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (message.type == MessageType.TEXT && !message.isRecalled) {
                            DropdownMenuItem(
                                text = { Text("复制") },
                                onClick = {
                                    showMenu = false
                                    onCopy(message.content)
                                },
                            )
                        }
                        if (localId != null && !message.isRecalled) {
                            DropdownMenuItem(
                                text = { Text("转发") },
                                onClick = {
                                    showMenu = false
                                    onForward(message)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("多选") },
                                onClick = {
                                    showMenu = false
                                    onEnterSelection(localId)
                                },
                            )
                        }
                        if (isOutgoing && !message.isRecalled && localId != null) {
                            if (message.status == MessageStatus.SENDING) {
                                DropdownMenuItem(
                                    text = { Text("取消发送") },
                                    onClick = {
                                        showMenu = false
                                        onDelete(localId)
                                    },
                                )
                            }
                            if (message.id > 0L) {
                                DropdownMenuItem(
                                    text = { Text("撤回") },
                                    onClick = {
                                        showMenu = false
                                        onRecall(message.id, localId)
                                    },
                                )
                            }
                        }
                        if (localId != null) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    showMenu = false
                                    onDelete(localId)
                                },
                            )
                        }
                    }
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
                MessageAvatar(avatarUrl, avatarContentDescription, onAvatarClick)
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

/** 非多选态下点击气泡：图片/视频走预览，分享卡片走跳转，文本不动。 */
private fun handleBubbleClick(
    message: Message,
    onMediaClick: (ChatMessageMediaViewer) -> Unit,
    onShareClick: (Message) -> Unit,
) {
    message.toChatMessageMediaViewer()?.let {
        onMediaClick(it)
        return
    }
    if (message.shareType != null) {
        onShareClick(message)
    }
}

@Composable
private fun MessageContent(
    message: Message,
    isPlayingVoice: Boolean,
    onPlayVoice: (Message) -> Unit,
    onMediaClick: (ChatMessageMediaViewer) -> Unit,
) {
    when {
        message.isRecalled -> Text(
            text = "消息已撤回",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        message.type == MessageType.TEXT -> Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
        )

        message.type == MessageType.AUDIO -> AudioContent(message, isPlayingVoice, onPlayVoice)

        // ⚠️ 媒体预览分支必须放在 FILE 之前：type=file + file_type=image（或文件名
        // 可识别为图片）时应渲染图片预览，否则会显示成「文件名 + 回形针」。
        // resolveMediaPreviewType 对真正的文档/其它文件返回 null，仍会落到 FILE 分支。
        message.resolveMediaPreviewType() != null -> MediaContent(message, onMediaClick)

        message.type == MessageType.FILE -> FileContent(message)

        message.shareType != null -> ShareContent(message)

        else -> Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AudioContent(
    message: Message,
    isPlaying: Boolean,
    onPlayVoice: (Message) -> Unit,
) {
    val seconds = ((message.audioDurationMs ?: 0L) / 1000L).coerceAtLeast(1L)
    // 气泡随语音时长变宽（与 Android 版一致），上限 +30s
    val minWidth = 110 + seconds.coerceAtMost(30L).toInt() * 4
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(min = minWidth.dp)
            .clickable { onPlayVoice(message) },
    ) {
        // 播放中：喇叭波纹按 0 → 1 → 2 → 0 循环，播完自动退回静止样式
        VoicePlayingIcon(
            isPlaying = isPlaying,
            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            size = 22.dp,
            modifier = Modifier.semantics {
                contentDescription = if (isPlaying) "停止播放" else "播放语音"
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${seconds}''",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FileContent(message: Message) {
    val name = message.localMediaFileName
        ?: message.content.takeIf { !it.startsWith("[") }
        ?: parseFileContentName(message.content)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 180.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaContent(
    message: Message,
    onMediaClick: (ChatMessageMediaViewer) -> Unit,
) {
    val viewer = message.toChatMessageMediaViewer() ?: return
    // ⚠️ Live 不能套视频的「播放」角标：它是「点开即自动播放的实况照片」，
    // 语义上是图片 + 一段实况视频，画一个播放键会被当成视频消息。
    // 与 Android ChatMessageItem.ImageMessageBubble 对齐：左上角挂 LIVE 徽章。
    val isVideo = viewer.previewType == com.fox.music.feature.chat.ui.component.ChatMediaPreviewType.VIDEO
    val isLive = viewer.previewType == com.fox.music.feature.chat.ui.component.ChatMediaPreviewType.LIVE
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onMediaClick(viewer) },
    ) {
        CachedImage(
            imageUrl = viewer.mediaUrl,
            contentDescription = viewer.fileName ?: "图片",
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        if (isLive) {
            PostLiveBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(22.dp),
            )
        }
    }
}

@Composable
private fun ShareContent(message: Message) {
    val data = message.shareData
    val title = data?.title?.takeIf { it.isNotBlank() }
        ?: data?.name?.takeIf { it.isNotBlank() }
        ?: data?.userDisplayName
        ?: when (message.shareType) {
            "music" -> "分享音乐"
            "playlist" -> "分享歌单"
            "album" -> "分享专辑"
            "artist" -> "分享歌手"
            "post" -> "分享帖子"
            "user" -> "推荐联系人"
            else -> "分享内容"
        }
    val cover = data?.coverImage?.takeIf { it.isNotBlank() }
        ?: data?.postCoverImage
        ?: data?.userCoverImage
    val subtitle = when (message.shareType) {
        "music" -> data?.artists?.joinToString("/") { it.name }?.takeIf { it.isNotBlank() }
        "post" -> data?.postBodyText
        "user" -> data?.userSignature
        "playlist" -> data?.description
        else -> data?.description
    }
    val fallbackIcon = if (message.shareType == "user") Icons.Default.Person else Icons.Default.Movie
    // 无图帖子不展示封面（含占位图）：纯文字卡片，避免一个 Movie 占位图误导。
    // 音乐/歌单/专辑/歌手/名片等类型仍保留封面位（空 URL 走占位图标）。
    val showCover = cover != null || message.shareType != "post"

    Row(
        modifier = Modifier.widthIn(max = 240.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            CachedImage(
                imageUrl = cover,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(6.dp),
                placeholderIcon = fallbackIcon,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle?.takeIf { it.isNotBlank() } ?: labelForShareType(message.shareType),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun labelForShareType(shareType: String?): String = when (shareType) {
    "music" -> "歌曲"
    "playlist" -> "歌单"
    "album" -> "专辑"
    "artist" -> "歌手"
    "post" -> "帖子"
    "user" -> "用户"
    else -> "分享"
}

@Composable
private fun MessageAvatar(
    imageUrl: String?,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        CachedImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
    }
}
