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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.download.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadActionBottomSheet(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
) {
    // 统一走 FoxSheet：22dp 顶圆角 + drag handle + 语义 surface 底
    FoxSheet(onDismissRequest = onDismiss) {
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
                    imageUrl = task.coverUrl,
                    contentDescription = task.title,
                    modifier = Modifier.size(64.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.artistNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.artistNames,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider()

            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                    ActionRow(
                        icon = Icons.Default.Pause,
                        label = "暂停下载",
                        onClick = {
                            onPause()
                            onDismiss()
                        },
                    )
                }
                DownloadStatus.PAUSED -> {
                    ActionRow(
                        icon = Icons.Default.PlayArrow,
                        label = "继续下载",
                        onClick = {
                            onResume()
                            onDismiss()
                        },
                    )
                }
                DownloadStatus.FAILED -> {
                    ActionRow(
                        icon = Icons.Default.Refresh,
                        label = "重新下载",
                        onClick = {
                            onResume()
                            onDismiss()
                        },
                    )
                }
                DownloadStatus.COMPLETED -> {
                    ActionRow(
                        icon = Icons.Default.SkipNext,
                        label = "下一首播放",
                        onClick = {
                            onPlayNext()
                            onDismiss()
                        },
                    )
                }
            }

            ActionRow(
                icon = Icons.Default.Delete,
                label = "删除",
                onClick = {
                    onDelete()
                    onDismiss()
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
