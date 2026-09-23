package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

@Composable
fun VoiceHoldButton(
    enabled: Boolean,
    isRecording: Boolean,
    showHoldToSpeakBar: Boolean,
    recordingDurationSec: Int,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCancelZone by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .then(
                if (showHoldToSpeakBar || isRecording) {
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier
                },
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cancelThresholdPx = -80.dp.toPx()
                    var cancelled = false
                    var totalDeltaY = 0f
                    isCancelZone = false
                    onPressStart()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (cancelled) {
                                    onPressCancel()
                                } else {
                                    onPressEnd()
                                }
                                break
                            }
                            totalDeltaY += change.positionChange().y
                            if (!cancelled && totalDeltaY < cancelThresholdPx) {
                                cancelled = true
                                isCancelZone = true
                            } else if (cancelled && totalDeltaY >= cancelThresholdPx) {
                                cancelled = false
                                isCancelZone = false
                            }
                        }
                    } finally {
                        isCancelZone = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isRecording -> {
                Text(
                    text = if (isCancelZone) {
                        "松开手指，取消发送"
                    } else {
                        "松开发送  ${recordingDurationSec}s"
                    },
                    color = if (isCancelZone) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            showHoldToSpeakBar -> {
                Text(
                    text = "按住 说话",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
