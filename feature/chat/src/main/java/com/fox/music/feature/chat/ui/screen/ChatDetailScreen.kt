package com.fox.music.feature.chat.ui.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
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
    val scope = rememberCoroutineScope()
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
                                        fileName = confirmed.fileName ?: "video_${System.currentTimeMillis()}.mp4",
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("聊天") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
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
        },
    ) { padding ->
        if (state.isLoading && state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无聊天记录，发送第一条消息吧",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true,
        ) {
            items(
                items = state.messages.reversed(),
                key = { it.localId ?: it.id.toString() },
            ) { message ->
                val isOutgoing = message.senderId == state.currentUserId
                ChatMessageItem(
                    message = message,
                    isOutgoing = isOutgoing,
                    avatarUrl = if (isOutgoing) state.currentUserAvatar else state.peerAvatar,
                    avatarContentDescription = if (isOutgoing) "我的头像" else state.peerNickname,
                    onRetry = { viewModel.sendIntent(ChatDetailIntent.RetryMessage(it)) },
                    onMediaClick = { viewingMessageMedia = it },
                    onFileClick = { openFileWithSystem(context, it) },
                )
            }
        }
    }
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
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> Uri.parse(raw)
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
