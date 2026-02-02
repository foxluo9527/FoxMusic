package com.fox.music.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.Music
import com.fox.music.core.model.PlayerState
import com.fox.music.core.ui.theme.FoxMusicTheme

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val music = playerState.currentMusic ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
    ) {
        Column {
            if (playerState.duration > 0) {
                LinearProgressIndicator(
                    progress = { (playerState.position.toFloat() / playerState.duration).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CachedImage(
                    imageUrl = music.coverImage,
                    contentDescription = music.title,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = music.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = music.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNextClick) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next track",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MiniPlayerPreview() {
    FoxMusicTheme {
        MiniPlayer(
            playerState = PlayerState(
                currentMusic = Music(
                    id = 1,
                    title = "Sample Song",
                    url = "",
                    duration = 240000,
                ),
                isPlaying = true,
                position = 60000,
                duration = 240000,
            ),
            onPlayPauseClick = {},
            onNextClick = {},
        )
    }
}
