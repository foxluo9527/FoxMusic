package com.fox.music.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import com.fox.music.core.common.util.MediaUrlResolver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.Music

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicActionBottomSheet(
    music: Music,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onReport: () -> Unit,
    onArtistClick: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showDownload = onDownload != null && !MediaUrlResolver.isLocalMedia(music.url)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                CachedImage(
                    imageUrl = music.coverImage,
                    contentDescription = music.title,
                    modifier = Modifier.size(64.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = music.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (music.artists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            items(music.artists, key = { it.id }) { artist ->
                                ArtistChip(
                                    artist = artist,
                                    enabled = !music.isThirdParty,
                                    onClick = {
                                        if (!music.isThirdParty) {
                                            onDismiss()
                                            onArtistClick(artist.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            ActionRow(
                icon = Icons.Default.SkipNext,
                label = "下一首播放",
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
            )
            if (!music.isThirdParty) {
                ActionRow(
                    icon = Icons.Default.PlaylistAdd,
                    label = "添加到歌单",
                    onClick = {
                        onAddToPlaylist()
                    },
                )
            }
            if (!music.isThirdParty && onShare != null) {
                ActionRow(
                    icon = Icons.Default.Share,
                    label = "分享给好友",
                    onClick = {
                        onShare()
                        onDismiss()
                    },
                )
            }
            if (showDownload) {
                ActionRow(
                    icon = Icons.Default.CloudDownload,
                    label = "下载歌曲",
                    onClick = {
                        onDownload?.invoke()
                    },
                )
            }
            ActionRow(
                icon = Icons.Default.Flag,
                label = "举报",
                onClick = {
                    onReport()
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
