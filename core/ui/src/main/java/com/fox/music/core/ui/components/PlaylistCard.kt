package com.fox.music.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.Playlist
import com.fox.music.core.ui.theme.FoxMusicTheme

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        CachedImage(
            imageUrl = playlist.coverImage,
            contentDescription = playlist.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
            placeholderIcon = Icons.Filled.QueueMusic,
        )
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${playlist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun PlaylistCardPreview() {
    FoxMusicTheme {
        PlaylistCard(
            playlist = Playlist(
                id = 1,
                title = "Chill Vibes",
                trackCount = 24,
            ),
            onClick = {},
        )
    }
}
