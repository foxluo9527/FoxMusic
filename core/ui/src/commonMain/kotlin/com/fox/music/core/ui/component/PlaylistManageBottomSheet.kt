package com.fox.music.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistManageBottomSheet(
    playlistTitle: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayAll: () -> Unit,
) {
    // 统一走 FoxSheet：22dp 顶圆角 + drag handle + 语义 surface 底
    FoxSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = playlistTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            PlaylistManageAction(
                icon = Icons.Default.PlayArrow,
                title = "全部播放",
                subtitle = "播放歌单内全部歌曲",
                onClick = {
                    onPlayAll()
                    onDismiss()
                },
            )
            PlaylistManageAction(
                icon = Icons.Default.Edit,
                title = "编辑歌单",
                subtitle = "修改名称、封面和描述",
                onClick = {
                    onEdit()
                    onDismiss()
                },
            )
            PlaylistManageAction(
                icon = Icons.Default.Delete,
                title = "删除歌单",
                subtitle = "删除后不可恢复",
                onClick = {
                    onDelete()
                    onDismiss()
                },
                tint = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlaylistManageAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == MaterialTheme.colorScheme.error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 删除歌单确认 —— 走图标居中破坏性确认，取代原来手写的 AlertDialog
 * （旧实现圆角/按钮排布全用 M3 默认值，与全站 20dp 纸感容器对不上）。
 */
@Composable
fun DeletePlaylistConfirmDialog(
    playlistTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    FoxConfirmDialog(
        title = "删除歌单？",
        text = "「$playlistTitle」将被永久删除，此操作不可恢复。",
        confirmText = "删除",
        dismissText = "取消",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        destructive = true,
        icon = Icons.Default.Delete,
    )
}
