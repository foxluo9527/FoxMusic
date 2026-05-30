package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AttachmentItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun ChatAttachmentPanel(
    onPickMedia: () -> Unit,
    onTakePhoto: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        AttachmentItem(Icons.Default.Image, "媒体", onPickMedia),
        AttachmentItem(Icons.Default.CameraAlt, "拍照", onTakePhoto),
        AttachmentItem(Icons.Default.Videocam, "拍摄", onCaptureVideo),
        AttachmentItem(Icons.Default.AttachFile, "文件", onPickFile),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items) { item ->
                AttachmentGridItem(
                    icon = item.icon,
                    label = item.label,
                    onClick = item.onClick,
                )
            }
        }
    }
}

@Composable
private fun AttachmentGridItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(56.dp)
                    .padding(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
