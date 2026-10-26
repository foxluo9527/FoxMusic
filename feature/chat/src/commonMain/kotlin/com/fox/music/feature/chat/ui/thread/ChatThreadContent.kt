package com.fox.music.feature.chat.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.ui.component.FoxConfirmDialog
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarIconAction
import com.fox.music.core.ui.component.PlatformFileImage
import com.fox.music.feature.chat.ui.component.ChatInputBar
import com.fox.music.feature.chat.ui.component.EmojiPickerPanel
import com.fox.music.feature.chat.ui.util.ChatMessageMediaViewer
import com.fox.music.feature.chat.util.shouldShowMessageTime

/**
 * 聊天线程页（会话详情）—— 跨端无状态实现。
 *
 * 由宿主提供 [ChatThreadState] 与全部回调：
 *  - Android 有 ChatDetailViewModel（Room + 上传器）；iOS 由 iosShared 的 FoxChatDetailScreen
 *    用 Ktor REST 组装（见 ChatThreadContract 的说明）。
 *  - 语音播放、相册/拍照/文件选择、媒体预览都**不在本组件内**（平台相关），
 *    通过回调交给宿主；宿主没有能力时给出明确提示即可。
 *
 * 顶部为 M3 `TopAppBar`，因此 iOS 宿主必须 `pageOwnsTopBar = true`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadContent(
    state: ChatThreadState,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onMoreClick: () -> Unit,
    onAvatarClick: (Long) -> Unit,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onSendEmoji: (String) -> Unit,
    onToggleEmoji: () -> Unit,
    onToggleAttachment: () -> Unit,
    onToggleVoiceInputMode: () -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: () -> Unit,
    onVoicePressCancel: () -> Unit,
    onPickMedia: () -> Unit,
    onTakePhoto: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPickFile: () -> Unit,
    onRetry: (String) -> Unit,
    onRecall: (Long, String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit,
    onForward: (Message) -> Unit,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onExitSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMediaClick: (ChatMessageMediaViewer) -> Unit,
    onShareClick: (Message) -> Unit,
    onPlayVoice: (Message) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 是否在右上角显示「聊天设置」齿轮。iOS 端没有独立齿轮入口（聊天信息页由
     * 「更多」图标进入，对齐 Android ChatDetailScreen 的 MoreVert → 设置页路径），
     * 传 false 隐藏，只保留「更多」图标。
     */
    showSettingsAction: Boolean = true,
) {
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 新消息到达时贴到底部（reverseLayout 下 index 0 就是最新）
    val messageCount = state.messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0) listState.animateScrollToItem(0)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = if (state.isSelectionMode) {
                    "已选择 ${state.selectedCount} 条"
                } else {
                    state.title
                },
                showBackButton = true,
                onBackClick = if (state.isSelectionMode) onExitSelection else onBack,
                actions = {
                    if (state.isSelectionMode) {
                        FoxTopBarIconAction(
                            icon = Icons.Default.Delete,
                            contentDescription = "删除所选",
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = state.selectedCount > 0,
                        )
                    } else {
                        if (showSettingsAction) {
                            FoxTopBarIconAction(
                                icon = Icons.Default.Settings,
                                contentDescription = "聊天设置",
                                onClick = onSettingsClick,
                            )
                        }
                        FoxTopBarIconAction(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            onClick = onMoreClick,
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!state.isSelectionMode) {
                ChatInputBar(
                    inputText = state.inputText,
                    isSending = state.isSending,
                    isVoiceInputMode = state.isVoiceInputMode,
                    isRecordingVoice = state.isRecordingVoice,
                    recordingDurationSec = state.recordingDurationSec,
                    showEmojiPanel = state.showEmojiPanel,
                    showAttachmentPanel = state.showAttachmentPanel,
                    onInputChange = onInputChange,
                    onSendClick = onSendClick,
                    onToggleEmoji = onToggleEmoji,
                    onToggleAttachment = onToggleAttachment,
                    onToggleVoiceInputMode = onToggleVoiceInputMode,
                    onInputFocusChanged = {},
                    onPickMedia = onPickMedia,
                    onTakePhoto = onTakePhoto,
                    onCaptureVideo = onCaptureVideo,
                    onPickFile = onPickFile,
                    onVoicePressStart = onVoicePressStart,
                    onVoicePressEnd = onVoicePressEnd,
                    onVoicePressCancel = onVoicePressCancel,
                    emojiPanel = {
                        EmojiPickerPanel(onEmojiSelected = onSendEmoji)
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .imePadding()
                        .navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        // 背景层 + 内容层共用一个贴满内容区的 Box（背景只在消息区域内铺，
        // 顶栏/输入栏保持 surface 底色，对齐 Android ChatDetailScreen 的叠放方式）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.backgroundImagePath?.let { path ->
                PlatformFileImage(
                    filePath = path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // 30% 黑色遮罩：背景图上气泡/文字才看得清（与 Android 同一参数）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                )
            }

            if (state.isLoading && state.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (state.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error ?: "还没有消息，打个招呼吧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.backgroundImagePath != null) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                return@Scaffold
            }

            // reverseLayout 让列表天然停在底部；数据按时间倒序喂进去，
            // 于是「上一条」在倒序表里是 index + 1。
            val reversed = remember(state.messages) { state.messages.asReversed() }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(
                    count = reversed.size,
                    key = { index -> reversed[index].localId ?: "idx_$index" },
                ) { index ->
                    val message = reversed[index]
                    val previous = reversed.getOrNull(index + 1)
                    val showTime = shouldShowMessageTime(
                        currentMillis = parseCreatedAt(message),
                        previousMillis = previous?.let { parseCreatedAt(it) },
                    )
                    val isOutgoing = message.senderId == state.currentUserId

                    ChatMessageBubble(
                        message = message,
                        isOutgoing = isOutgoing,
                        avatarUrl = if (isOutgoing) state.currentUserAvatar else state.peerAvatar,
                        avatarContentDescription = if (isOutgoing) "我" else state.peerNickname,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = message.localId?.let { it in state.selectedLocalIds } == true,
                        isPlayingVoice = message.localId != null &&
                            message.localId == state.playingVoiceLocalId,
                        onAvatarClick = { onAvatarClick(message.senderId) },
                        onRetry = onRetry,
                        onRecall = onRecall,
                        onDelete = onDelete,
                        onCopy = onCopy,
                        onForward = onForward,
                        onEnterSelection = onEnterSelection,
                        onToggleSelection = onToggleSelection,
                        onMediaClick = onMediaClick,
                        onShareClick = onShareClick,
                        onPlayVoice = onPlayVoice,
                        showTime = showTime,
                    )
                }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        // 全局弹窗扫尾（设计稿⑨）：破坏性确认统一走 FoxConfirmDialog
        FoxConfirmDialog(
            title = "删除消息？",
            text = "将删除选中的 ${state.selectedCount} 条消息，删除后无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            icon = Icons.Default.DeleteOutline,
            onDismiss = { showDeleteSelectedDialog = false },
            onConfirm = {
                showDeleteSelectedDialog = false
                onDeleteSelected()
            },
        )
    }
}

/** 消息时间戳 → epoch millis；解析不出来时返回 0（只会让时间条多显示一次）。 */
private fun parseCreatedAt(message: Message): Long =
    com.fox.music.feature.chat.util.parseTimestampMillis(message.createdAt) ?: 0L

/** 消息里出现「发送中」时视作列表正在忙（供顶部/输入条展示进度）。 */
internal fun ChatThreadState.hasSendingMessage(): Boolean =
    messages.any { it.status == MessageStatus.SENDING }
