package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MusicSelectionBottomBar(
    selectedCount: Int,
    showRemoveFromPlaylist: Boolean,
    onAddToQueue: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "已选 $selectedCount 首",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionAction(
                    icon = Icons.Default.QueueMusic,
                    label = "播放列表",
                    onClick = onAddToQueue,
                )
                if (showRemoveFromPlaylist) {
                    SelectionAction(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        onClick = onRemoveFromPlaylist,
                    )
                }
                SelectionAction(
                    icon = Icons.Default.PlaylistAdd,
                    label = "歌单",
                    onClick = onAddToPlaylist,
                )
                SelectionAction(
                    icon = Icons.Default.Download,
                    label = "下载",
                    onClick = onDownload,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
