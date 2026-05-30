package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val InputBarIconSize = 36.dp
private val InputBarIconInnerSize = 22.dp
private val InputBarSpacing = 2.dp

@Composable
fun ChatInputBar(
    inputText: String,
    isSending: Boolean,
    isVoiceInputMode: Boolean,
    isRecordingVoice: Boolean,
    recordingDurationSec: Int,
    showEmojiPanel: Boolean,
    showAttachmentPanel: Boolean,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onToggleEmoji: () -> Unit,
    onToggleAttachment: () -> Unit,
    onToggleVoiceInputMode: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onPickMedia: () -> Unit,
    onTakePhoto: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPickFile: () -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: () -> Unit,
    onVoicePressCancel: () -> Unit,
    emojiPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InputBarSpacing),
        ) {
            CompactIconButton(
                onClick = onToggleVoiceInputMode,
                enabled = !isRecordingVoice,
                contentDescription = if (isVoiceInputMode) "切换到键盘" else "切换到语音",
            ) {
                Icon(
                    imageVector = if (isVoiceInputMode) Icons.Default.Keyboard else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(InputBarIconInnerSize),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isVoiceInputMode || isRecordingVoice) {
                    VoiceHoldButton(
                        enabled = !isSending,
                        isRecording = isRecordingVoice,
                        showHoldToSpeakBar = isVoiceInputMode && !isRecordingVoice,
                        recordingDurationSec = recordingDurationSec,
                        onPressStart = onVoicePressStart,
                        onPressEnd = onVoicePressEnd,
                        onPressCancel = onVoicePressCancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ChatTextInput(
                        value = inputText,
                        onValueChange = onInputChange,
                        onFocusChanged = onInputFocusChanged,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!isRecordingVoice) {
                CompactIconButton(
                    onClick = onToggleEmoji,
                    contentDescription = "表情",
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = null,
                        modifier = Modifier.size(InputBarIconInnerSize),
                        tint = if (showEmojiPanel) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                if (inputText.isNotBlank()) {
                    CompactIconButton(
                        onClick = onSendClick,
                        enabled = !isSending,
                        contentDescription = "发送",
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(InputBarIconInnerSize),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    RoundedBorderPlusButton(
                        onClick = onToggleAttachment,
                        isActive = showAttachmentPanel,
                    )
                }
            }
        }

        if (showEmojiPanel) {
            emojiPanel()
        }

        if (showAttachmentPanel) {
            ChatAttachmentPanel(
                onPickMedia = onPickMedia,
                onTakePhoto = onTakePhoto,
                onCaptureVideo = onCaptureVideo,
                onPickFile = onPickFile,
            )
        }
    }
}

@Composable
private fun ChatTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textStyle = TextStyle(
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        textStyle = textStyle,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = "输入消息...",
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(InputBarIconSize),
    ) {
        content()
    }
}

@Composable
private fun RoundedBorderPlusButton(
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    val iconTint = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.size(InputBarIconSize),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .border(width = 1.5.dp, color = borderColor, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "更多",
                modifier = Modifier.size(14.dp),
                tint = iconTint,
            )
        }
    }
}
