package com.fox.music.feature.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.PlayerState
import com.fox.music.core.player.controller.MusicController

/**
 *    Author : 罗福林
 *    Date   : 2026/2/4
 *    Desc   :
 */
private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongProgress(
    modifier: Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    contrastColor: Color
) {
    val position by musicController.currentPosition.collectAsState(0L)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var nextPosition by remember {mutableFloatStateOf(0f)}
        val progress by remember(playerState, nextPosition) {
            derivedStateOf {
                if (nextPosition == 0f) {
                    playerState.position / (playerState.duration * 1f)
                } else {
                    nextPosition / (playerState.duration * 1f)
                }
            }
        }
        Text(
            text = formatMs(position),
            style = MaterialTheme.typography.labelSmall,
            color = contrastColor
        )
        Slider(
            value = if (nextPosition == 0f) position.toFloat()
                .coerceIn(
                    0f,
                    playerState.position.toFloat().coerceAtLeast(1f)
                ) else nextPosition,
            onValueChange = {nextPosition = it},
            onValueChangeFinished = {
                musicController.seekTo(nextPosition.toLong())
                nextPosition = 0f
            },
            valueRange = 0f .. (playerState.duration.toFloat().coerceAtLeast(1f)),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            track = {state ->
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (state.isDragging) 16.dp else 8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(if (state.isDragging) 16.dp else 8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)
                            )
                    )
                }
            },
            thumb = {
                Box(
                    Modifier
                        .padding(2.dp)
                        .background(Color.Gray, CircleShape)
                ) {
                    Row(
                        Modifier
                            .size(26.dp, 16.dp)
                            .background(Color.White, CircleShape),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(
                            Modifier
                                .height(10.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .height(10.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(
                            Modifier
                                .height(10.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

            }
        )
        Text(
            text = formatMs(playerState.duration),
            style = MaterialTheme.typography.labelSmall,
            color = contrastColor
        )
    }
}
