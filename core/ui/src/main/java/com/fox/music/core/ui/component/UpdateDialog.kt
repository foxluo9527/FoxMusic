package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.app.AppUpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    forceUpdate: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadIndeterminate: Boolean = false,
    downloadStatusText: String? = null,
    error: String?,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = updateInfo.upgradeTitle
        ?: "发现新版本 ${updateInfo.latestVersionName}"
    val upgradeContent = updateInfo.upgradeContent

    AlertDialog(
        onDismissRequest = {
            if (!forceUpdate && !isDownloading) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (!upgradeContent.isNullOrBlank()) {
                    Text(
                        text = upgradeContent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (updateInfo.changelog.isNotEmpty()) {
                    updateInfo.changelog.forEach { item ->
                        Text(
                            text = "• $item",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (downloadIndeterminate) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadStatusText ?: "下载中 $downloadProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when {
                !error.isNullOrBlank() -> {
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
                isDownloading -> Unit
                else -> {
                    TextButton(onClick = onConfirmUpdate) {
                        Text("立即更新")
                    }
                }
            }
        },
        dismissButton = {
            if (!forceUpdate && !isDownloading && error.isNullOrBlank()) {
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        },
    )
}
