package com.fox.music.feature.chat.ui.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.chat.Message
import com.fox.music.feature.chat.ui.component.ChatInputBar
import com.fox.music.feature.chat.ui.component.ChatMediaPreviewDialog
import com.fox.music.feature.chat.ui.component.ChatMediaPreviewType
import com.fox.music.feature.chat.ui.component.ChatMessageItem
import com.fox.music.feature.chat.ui.component.ChatMessageMediaViewer
import com.fox.music.feature.chat.ui.component.ChatMessageMediaViewerDialog
import com.fox.music.feature.chat.ui.component.ChatPendingMedia
import com.fox.music.feature.chat.ui.component.EmojiPickerPanel
import com.fox.music.feature.chat.ui.model.ChatMediaItem
import com.fox.music.feature.chat.ui.util.ChatCameraHelper
import com.fox.music.feature.chat.ui.util.ChatImageCropHelper
import com.fox.music.feature.chat.ui.util.ChatVideoCompressor
import com.fox.music.feature.chat.ui.util.parseFileContentName
import com.fox.music.feature.chat.util.VoiceRecorder
import com.fox.music.feature.chat.util.parseTimestampMillis
import com.fox.music.feature.chat.util.shouldShowMessageTime
import com.fox.music.feature.chat.viewmodel.ChatDetailEffect
import com.fox.music.feature.chat.viewmodel.ChatDetailIntent
import com.fox.music.feature.chat.viewmodel.ChatDetailViewModel
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToSettings: (Long, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToSelectFriend: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val voiceRecorder = remember { VoiceRecorder(context) }
    var recordStartTime by remember { mutableLongStateOf(0L) }
    var isVoicePressing by remember { mutableStateOf(false) }
    var pendingMedia by remember { mutableStateOf<ChatPendingMedia?>(null) }
    var viewingMessageMedia by remember { mutableStateOf<ChatMessageMediaViewer?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var processingText by remember { mutableStateOf("处理中...") }
    var showMediaSelector by remember { mutableStateOf(false) }
    var pendingRecall by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingForwardMessage by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()

    // Listen for selected friend from SelectFriendScreen
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry) {
        val entry = backStackEntry ?: return@LaunchedEffect
        val friendId = entry.savedStateHandle.get<Long>("selectedFriendId") ?: return@LaunchedEffect
        val friendNickname = entry.savedStateHandle.get<String>("selectedFriendNickname") ?: ""
        val friendAvatar = entry.savedStateHandle.get<String>("selectedFriendAvatar") ?: ""

        // Clear the saved state to avoid re-triggering
        entry.savedStateHandle.remove<Long>("selectedFriendId")
        entry.savedStateHandle.remove<String>("selectedFriendNickname")
        entry.savedStateHandle.remove<String>("selectedFriendAvatar")

        // Forward the pending message to the selected friend
        val messageToForward = pendingForwardMessage
        if (messageToForward != null && friendId > 0) {
            pendingForwardMessage = null
            viewModel.forwardMessage(friendId, messageToForward)
        }
    }
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = result.data?.let(UCrop::getOutput)
            croppedUri?.let { uri ->
                pendingMedia = pendingMedia?.copy(uri = uri) ?: ChatPendingMedia(
                    uri = uri,
                    type = ChatMediaPreviewType.IMAGE,
                )
            }
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val fileName = context.resolveDisplayName(it)
            viewModel.sendIntent(ChatDetailIntent.SendFile(it, fileName))
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                pendingMedia = ChatPendingMedia(
                    uri = uri,
                    type = ChatMediaPreviewType.IMAGE,
                    fileName = "photo_${System.currentTimeMillis()}.jpg",
                )
            }
        }
        pendingCameraUri = null
    }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                pendingMedia = ChatPendingMedia(
                    uri = uri,
                    type = ChatMediaPreviewType.VIDEO,
                    fileName = "video_${System.currentTimeMillis()}.mp4",
                )
            }
        }
        pendingCameraUri = null
    }

    fun dismissAttachmentSheet() {
        if (state.showAttachmentSheet) {
            viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet)
        }
    }

    fun showMediaSelectorPanel() {
        dismissAttachmentSheet()
        showMediaSelector = true
    }

    fun onMediaPicked(item: ChatMediaItem) {
        pendingMedia = ChatPendingMedia(
            uri = item.uri,
            type = item.type,
            fileName = item.displayName.ifBlank { null },
        )
    }


    fun requestCameraPermission(onGranted: () -> Unit) {
        XXPermissions.with(context)
            .permission(Permission.CAMERA)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) onGranted()
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
                }
            })
    }


    fun requestVideoCapturePermission(onGranted: () -> Unit) {
        XXPermissions.with(context)
            .permission(Permission.CAMERA, Permission.RECORD_AUDIO)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) onGranted()
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    Toast.makeText(context, "需要相机和麦克风权限才能拍摄视频", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }



    LaunchedEffect(Unit) {
        viewModel.sendIntent(ChatDetailIntent.Load)
    }

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible) {
            viewModel.sendIntent(ChatDetailIntent.DismissInputPanels)
        }
    }

    LaunchedEffect(state.isVoiceInputMode) {
        if (state.isVoiceInputMode) {
            focusManager.clearFocus()
            viewModel.sendIntent(ChatDetailIntent.DismissInputPanels)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ChatDetailEffect.NavigateBack -> onBack()
                is ChatDetailEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                ChatDetailEffect.RequestRecordPermission -> Unit
            }
        }
    }

    LaunchedEffect(state.isRecordingVoice) {
        if (!state.isRecordingVoice) return@LaunchedEffect
        var seconds = 0
        while (true) {
            delay(1000)
            seconds++
            viewModel.sendIntent(ChatDetailIntent.UpdateRecordingDuration(seconds))
            if (!viewModel.uiState.value.isRecordingVoice) break
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }

    if (showMediaSelector) {
        ChatMediaSelectorScreen(
            onDismiss = { showMediaSelector = false },
            onMediaSelected = ::onMediaPicked,
        )
    }

    pendingMedia?.let { media ->
        ChatMediaPreviewDialog(
            media = media,
            onDismiss = { pendingMedia = null },
            onSend = { confirmed ->
                when (confirmed.type) {
                    ChatMediaPreviewType.IMAGE -> {
                        showMediaSelector = false
                        pendingMedia = null
                        viewModel.sendIntent(
                            ChatDetailIntent.SendImage(
                                uri = confirmed.uri,
                                sendOriginal = confirmed.sendOriginal,
                                fileName = confirmed.fileName,
                            ),
                        )
                    }

                    ChatMediaPreviewType.VIDEO -> {
                        scope.launch {
                            runCatching {
                                showMediaSelector = false
                                val targetUri = if (confirmed.sendOriginal) {
                                    confirmed.uri
                                } else {
                                    isProcessingMedia = true
                                    processingText = "正在压缩视频..."
                                    ChatVideoCompressor.compressTo720p(context, confirmed.uri)
                                }
                                pendingMedia = null
                                viewModel.sendIntent(
                                    ChatDetailIntent.SendVideo(
                                        uri = targetUri,
                                        fileName = confirmed.fileName
                                            ?: "video_${System.currentTimeMillis()}.mp4",
                                    ),
                                )
                            }.onFailure { e ->
                                Toast.makeText(
                                    context,
                                    e.message ?: "视频压缩失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            isProcessingMedia = false
                        }
                    }
                }
            },

            onCrop = { uri ->
                cropLauncher.launch(ChatImageCropHelper.createCropIntent(context, uri))
            },
            onSendOriginalChange = { sendOriginal ->
                pendingMedia = media.copy(sendOriginal = sendOriginal)
            },
            isProcessing = isProcessingMedia,
            processingText = processingText,
        )
    }

    viewingMessageMedia?.let { viewer ->
        ChatMessageMediaViewerDialog(
            viewer = viewer,
            onDismiss = { viewingMessageMedia = null },
        )
    }

    pendingRecall?.let { (messageId, localId) ->
        RecallConfirmDialog(
            onConfirm = {
                viewModel.sendIntent(ChatDetailIntent.RecallMessage(messageId, localId))
                pendingRecall = null
            },
            onDismiss = { pendingRecall = null },
        )
    }

    pendingDelete?.let { localId ->
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.sendIntent(ChatDetailIntent.DeleteMessage(localId))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isSelectionMode) {
                            "已选择 ${state.selectedMessageIds.size} 项"
                        } else {
                            state.peerNickname ?: "聊天"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!state.isSelectionMode) {
                        IconButton(onClick = {
                            onNavigateToSettings(
                                state.userId,
                                state.peerNickname ?: "",
                                state.peerAvatar,
                            )
                        }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.isSelectionMode) {
                SelectionBottomBar(
                    selectedCount = state.selectedMessageIds.size,
                    onDeleteClick = {
                        viewModel.sendIntent(ChatDetailIntent.DeleteSelectedMessages)
                    },
                    onCancelClick = {
                        viewModel.sendIntent(ChatDetailIntent.ExitSelectionMode)
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    ChatInputBar(
                        inputText = state.inputText,
                        isSending = state.isSending,
                        isVoiceInputMode = state.isVoiceInputMode,
                        isRecordingVoice = state.isRecordingVoice,
                        recordingDurationSec = state.recordingDurationSec,
                        showEmojiPanel = state.showEmojiPanel,
                        showAttachmentPanel = state.showAttachmentSheet,
                        onInputChange = { viewModel.sendIntent(ChatDetailIntent.UpdateInput(it)) },
                        onSendClick = { viewModel.sendIntent(ChatDetailIntent.SendMessage) },
                        onToggleEmoji = {
                            focusManager.clearFocus()
                            viewModel.sendIntent(ChatDetailIntent.ToggleEmojiPanel)
                        },
                        onToggleAttachment = {
                            focusManager.clearFocus()
                            viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet)
                        },
                        onToggleVoiceInputMode = {
                            focusManager.clearFocus()
                            viewModel.sendIntent(ChatDetailIntent.ToggleVoiceInputMode)
                        },
                        onInputFocusChanged = { focused ->
                            if (focused) {
                                viewModel.sendIntent(ChatDetailIntent.DismissInputPanels)
                            }
                        },
                        onPickMedia = ::showMediaSelectorPanel,
                        onTakePhoto = {
                            dismissAttachmentSheet()
                            requestCameraPermission {
                                val uri = ChatCameraHelper.createImageUri(context)
                                pendingCameraUri = uri
                                takePictureLauncher.launch(uri)
                            }
                        },
                        onCaptureVideo = {
                            dismissAttachmentSheet()
                            requestVideoCapturePermission {
                                val uri = ChatCameraHelper.createVideoUri(context)
                                pendingCameraUri = uri
                                captureVideoLauncher.launch(uri)
                            }
                        },
                        onPickFile = {
                            dismissAttachmentSheet()
                            pickFileLauncher.launch("*/*")
                        },
                        onVoicePressStart = {
                            isVoicePressing = true
                            XXPermissions.with(context)
                                .permission(Permission.RECORD_AUDIO)
                                .request(object : OnPermissionCallback {
                                    override fun onGranted(
                                        permissions: MutableList<String>,
                                        allGranted: Boolean
                                    ) {
                                        if (allGranted && isVoicePressing && voiceRecorder.start()) {
                                            recordStartTime = System.currentTimeMillis()
                                            viewModel.sendIntent(ChatDetailIntent.StartVoiceRecord)
                                        }
                                    }

                                    override fun onDenied(
                                        permissions: MutableList<String>,
                                        doNotAskAgain: Boolean
                                    ) {
                                        isVoicePressing = false
                                        Toast.makeText(
                                            context,
                                            "需要录音权限才能发送语音",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                })
                        },

                        onVoicePressEnd = {
                            isVoicePressing = false
                            if (state.isRecordingVoice) {
                                val duration = System.currentTimeMillis() - recordStartTime
                                val uri = voiceRecorder.stop()
                                viewModel.sendIntent(ChatDetailIntent.StopVoiceRecord)
                                if (uri != null && duration >= 500L) {
                                    viewModel.sendIntent(ChatDetailIntent.SendVoice(uri, duration))
                                } else if (duration < 500L) {
                                    voiceRecorder.cancel()
                                    Toast.makeText(context, "说话时间太短", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onVoicePressCancel = {
                            isVoicePressing = false
                            if (state.isRecordingVoice) {
                                voiceRecorder.cancel()
                                viewModel.sendIntent(ChatDetailIntent.CancelVoiceRecord)
                            }
                        },
                        emojiPanel = {
                            EmojiPickerPanel(
                                onEmojiSelected = { viewModel.sendIntent(ChatDetailIntent.SendEmoji(it)) },
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.chatBackgroundPath?.let { path ->
                AsyncImage(
                    model = java.io.File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
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
                        text = "暂无聊天记录，发送第一条消息吧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.chatBackgroundPath != null) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true,
            ) {
            val reversedMessages = state.messages.reversed()
            itemsIndexed(
                items = reversedMessages,
                key = { _, message -> message.localId ?: message.id.toString() },
            ) { index, message ->
                val isOutgoing = message.senderId == state.currentUserId
                val localId = message.localId
                val isSelected = localId != null && localId in state.selectedMessageIds

                val previousMessage = if (index > 0) reversedMessages[index - 1] else null
                val showTime = if (previousMessage == null) {
                    true
                } else {
                    val currentMillis = parseTimestampMillis(message.createdAt)
                    val previousMillis = parseTimestampMillis(previousMessage.createdAt)
                    currentMillis == null || shouldShowMessageTime(currentMillis, previousMillis)
                }

                if (state.isSelectionMode && localId != null) {
                    SelectionMessageItem(
                        message = message,
                        isOutgoing = isOutgoing,
                        isSelected = isSelected,
                        avatarUrl = if (isOutgoing) state.currentUserAvatar else state.peerAvatar,
                        avatarContentDescription = if (isOutgoing) "我的头像" else state.peerNickname,
                        onToggleSelection = {
                            viewModel.sendIntent(ChatDetailIntent.ToggleMessageSelection(localId))
                        },
                        onMediaClick = { viewingMessageMedia = it },
                        onFileClick = { openFileWithSystem(context, it) },
                        showTime = showTime,
                    )
                } else {
                    ChatMessageItem(
                        message = message,
                        isOutgoing = isOutgoing,
                        avatarUrl = if (isOutgoing) state.currentUserAvatar else state.peerAvatar,
                        avatarContentDescription = if (isOutgoing) "我的头像" else state.peerNickname,
                        onRetry = { viewModel.sendIntent(ChatDetailIntent.RetryMessage(it)) },
                        onMediaClick = { viewingMessageMedia = it },
                        onFileClick = { openFileWithSystem(context, it) },
                        onDelete = { pendingDelete = it },
                        onRecall = { messageId, localId ->
                            pendingRecall = messageId to localId
                        },
                        onCancelSending = { viewModel.sendIntent(ChatDetailIntent.CancelSending(it)) },
                        onCopy = { content ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", content))
                            Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                        },
                        onForward = { msg -> pendingForwardMessage = msg },
                        onMultiSelect = { localId ->
                            viewModel.sendIntent(ChatDetailIntent.EnterSelectionMode(localId))
                        },
                        showTime = showTime,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SelectionMessageItem(
    message: Message,
    isOutgoing: Boolean,
    isSelected: Boolean,
    avatarUrl: String?,
    avatarContentDescription: String?,
    onToggleSelection: () -> Unit,
    onMediaClick: (ChatMessageMediaViewer) -> Unit = {},
    onFileClick: (Message) -> Unit = {},
    showTime: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelection)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
        )
        ChatMessageItem(
            message = message,
            isOutgoing = isOutgoing,
            avatarUrl = avatarUrl,
            avatarContentDescription = avatarContentDescription,
            onRetry = {},
            onMediaClick = onMediaClick,
            onFileClick = onFileClick,
            showTime = showTime,
        )
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancelClick) {
            Text("取消")
        }
        Button(
            onClick = onDeleteClick,
            enabled = selectedCount > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("删除记录 ($selectedCount)")
        }
    }
}

@Composable
private fun RecallConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("撤回消息") },
        text = { Text("确定要撤回这条消息吗？对方未接收的消息将被成功撤回。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除消息") },
        text = { Text("确定要删除这条消息吗？删除后将无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}


private fun android.content.Context.resolveDisplayName(uri: Uri): String? {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }
    return null
}

private fun openFileWithSystem(context: android.content.Context, message: Message) {
    val localRaw = message.localMediaUri?.takeIf { it.isNotBlank() }
    val remoteRaw = message.remoteMediaUrl?.takeIf { it.isNotBlank() }
    val raw = localRaw ?: remoteRaw ?: return
    val uri = when {
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith(
            "https://",
            ignoreCase = true
        ) -> Uri.parse(raw)

        raw.startsWith("file://", ignoreCase = true) -> {
            val file = File(raw.toUri().path.orEmpty())
            if (!file.exists()) {
                remoteRaw?.let { return@let Uri.parse(it) }
                Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }

        else -> Uri.parse(raw)
    }
    val fileName = message.localMediaFileName ?: parseFileContentName(message.content)
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val mimeType = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(ext)
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        if (it is ActivityNotFoundException) {
            Toast.makeText(context, "没有可用应用打开该文件", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "打开文件失败", Toast.LENGTH_SHORT).show()
        }
    }
}
