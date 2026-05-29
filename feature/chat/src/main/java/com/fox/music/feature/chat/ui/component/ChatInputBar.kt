package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    isSending: Boolean,
    isRecordingVoice: Boolean,
    recordingDurationSec: Int,
    showEmojiPanel: Boolean,
    showAttachmentSheet: Boolean,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onToggleEmoji: () -> Unit,
    onToggleAttachment: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: () -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: () -> Unit,
    onDismissAttachment: () -> Unit,
    emojiPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showEmojiPanel) {
            emojiPanel()
        }

        if (isRecordingVoice) {
            Text(
                text = "松开发送  ${recordingDurationSec}s",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onToggleAttachment) {
                    Icon(Icons.Default.AttachFile, contentDescription = "附件")
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    singleLine = true,
                )
                IconButton(onClick = onToggleEmoji) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "表情")
                }
                if (inputText.isNotBlank()) {
                    IconButton(onClick = onSendClick, enabled = !isSending) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                        }
                    }
                } else {
                    VoiceHoldButton(
                        enabled = !isSending,
                        onPressStart = onVoicePressStart,
                        onPressEnd = onVoicePressEnd,
                    )
                }
            }
        }

        if (showAttachmentSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onDismissAttachment,
                sheetState = sheetState,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AttachmentOption(Icons.Default.Image, "图片", onPickImage)
                    AttachmentOption(Icons.Default.Movie, "视频", onPickVideo)
                    AttachmentOption(Icons.Default.AttachFile, "文件", onPickFile)
                }
            }
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(text = label)
    }
}
