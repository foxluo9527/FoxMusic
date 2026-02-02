package com.fox.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.Music
import com.fox.music.core.model.RepeatMode
import com.fox.music.core.player.MusicController
import com.fox.music.core.ui.components.CachedImage

const val PLAYER_ROUTE = "player"

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    musicController: MusicController,
    onBack: () -> Unit = {},
) {
    val playerState by musicController.playerState.collectAsState(com.fox.music.core.model.PlayerState())
    val position by musicController.currentPosition.collectAsState(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Back")
            }
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            playerState.currentMusic?.let { music ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(320.dp),
                        shape = MaterialTheme.shapes.large
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = music.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = music.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMs(position),
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = position.toFloat().coerceIn(0f, playerState.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { musicController.seekTo(it.toLong()) },
                valueRange = 0f..(playerState.duration.toFloat().coerceAtLeast(1f)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = formatMs(playerState.duration),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { musicController.setRepeatMode(nextRepeatMode(playerState.repeatMode)) }) {
                Text(
                    text = when (playerState.repeatMode) {
                        RepeatMode.OFF -> "\uD83D\uDD01"
                        RepeatMode.ONE -> "1"
                        RepeatMode.ALL -> "\uD83D\uDD01"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            IconButton(onClick = { musicController.previous() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { if (playerState.isPlaying) musicController.pause() else musicController.play() }) {
                Icon(
                    if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(64.dp)
                )
            }
            IconButton(onClick = { musicController.next() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { musicController.toggleShuffle() }) {
                Text(
                    text = "\uD83D\uDD00",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}
