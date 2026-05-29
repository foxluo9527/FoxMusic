package com.fox.music.feature.chat.ui.screen

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.ui.component.ChatInputBar
import com.fox.music.feature.chat.ui.component.ChatMessageItem
import com.fox.music.feature.chat.ui.component.EmojiPickerPanel
import com.fox.music.feature.chat.ui.util.ChatImageCropHelper
import com.fox.music.feature.chat.util.VoiceRecorder
import com.fox.music.feature.chat.viewmodel.ChatDetailEffect
import com.fox.music.feature.chat.viewmodel.ChatDetailIntent
import com.fox.music.feature.chat.viewmodel.ChatDetailViewModel
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val voiceRecorder = remember { VoiceRecorder(context) }
    var recordStartTime by remember { mutableLongStateOf(0L) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = result.data?.let(UCrop::getOutput)
            croppedUri?.let { viewModel.sendIntent(ChatDetailIntent.SendImage(it)) }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            cropLauncher.launch(ChatImageCropHelper.createCropIntent(context, sourceUri))
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let {
            val fileName = context.resolveDisplayName(it)
            viewModel.sendIntent(ChatDetailIntent.SendVideo(it, fileName))
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

    LaunchedEffect(Unit) {
        viewModel.sendIntent(ChatDetailIntent.Load)
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
        recordStartTime = System.currentTimeMillis()
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

    Scaffold(
        modifier = modifier.imePadding(),
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
            ChatInputBar(
                inputText = state.inputText,
                isSending = state.isSending,
                isRecordingVoice = state.isRecordingVoice,
                recordingDurationSec = state.recordingDurationSec,
                showEmojiPanel = state.showEmojiPanel,
                showAttachmentSheet = state.showAttachmentSheet,
                onInputChange = { viewModel.sendIntent(ChatDetailIntent.UpdateInput(it)) },
                onSendClick = { viewModel.sendIntent(ChatDetailIntent.SendMessage) },
                onToggleEmoji = { viewModel.sendIntent(ChatDetailIntent.ToggleEmojiPanel) },
                onToggleAttachment = { viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet) },
                onPickImage = {
                    viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet)
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onPickVideo = {
                    viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet)
                    pickVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                onPickFile = {
                    viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet)
                    pickFileLauncher.launch("*/*")
                },
                onVoicePressStart = {
                    XXPermissions.with(context)
                        .permission(Permission.RECORD_AUDIO)
                        .request(object : OnPermissionCallback {
                            override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                                if (allGranted && voiceRecorder.start()) {
                                    viewModel.sendIntent(ChatDetailIntent.StartVoiceRecord)
                                }
                            }

                            override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                                Toast.makeText(context, "需要录音权限才能发送语音", Toast.LENGTH_SHORT).show()
                            }
                        })
                },
                onVoicePressEnd = {
                    if (state.isRecordingVoice) {
                        val duration = System.currentTimeMillis() - recordStartTime
                        val uri = voiceRecorder.stop()
                        viewModel.sendIntent(ChatDetailIntent.StopVoiceRecord)
                        if (uri != null && duration >= 500L) {
                            viewModel.sendIntent(ChatDetailIntent.SendVoice(uri, duration))
                        } else {
                            voiceRecorder.cancel()
                            if (duration < 500L) {
                                Toast.makeText(context, "说话时间太短", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismissAttachment = {
                    if (state.showAttachmentSheet) {
                        viewModel.sendIntent(ChatDetailIntent.ToggleAttachmentSheet)
                    }
                },
                emojiPanel = {
                    EmojiPickerPanel(
                        onEmojiSelected = { viewModel.sendIntent(ChatDetailIntent.SendEmoji(it)) },
                    )
                },
            )
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
                ChatMessageItem(
                    message = message,
                    isOutgoing = message.senderId == state.currentUserId,
                    onRetry = { viewModel.sendIntent(ChatDetailIntent.RetryMessage(it)) },
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
