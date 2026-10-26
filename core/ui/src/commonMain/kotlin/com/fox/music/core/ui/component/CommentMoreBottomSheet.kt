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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// FoxSheet 的 sheetState 参数把实验性的 SheetState 带进了它的签名，
// 这里需要显式 opt-in（与迁移前直接调 ModalBottomSheet 时一样）。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentMoreBottomSheet(
    isOwnComment: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    // 统一走 FoxSheet：22dp 顶圆角 + drag handle + 语义 surface 底
    FoxSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (isOwnComment) {
                CommentMoreActionRow(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    onClick = {
                        onDismiss()
                        onDelete()
                    },
                )
            } else {
                CommentMoreActionRow(
                    icon = Icons.Default.Flag,
                    label = "举报",
                    onClick = {
                        onDismiss()
                        onReport()
                    },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CommentMoreActionRow(
    icon: ImageVector,
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
