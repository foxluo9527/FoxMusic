package com.fox.music.iosshared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fox.music.iosshared.foxComposeViewController
import platform.UIKit.UIViewController

/**
 * 发布任务状态页（iOS）。对应 Android PostPublishQueueScreen + PostPublishQueueViewModel。
 * 展示 [FoxPostPublishQueue] 中的后台任务（上传 / 发布进度、失败重试 / 取消）。
 */
object FoxPostPublishQueueScreen {

    fun makeViewController(
        onBack: () -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController {
        val jobs by FoxPostPublishQueue.observeJobs().collectAsState()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
                Spacer(Modifier.weight(1f))
                Text("发布任务", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (jobs.isEmpty()) {
                Text("暂无发布任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(jobs, key = { it.jobId }) { job ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(job.title.ifBlank { job.content.take(20) }.ifBlank { "新动态" })
                                Text(
                                    text = if (job.status == FoxPostJobStatus.FAILED && job.errorMessage != null) {
                                        "${job.status.label}：${job.errorMessage}"
                                    } else {
                                        job.status.label
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (job.status == FoxPostJobStatus.FAILED) {
                                        Color(0xFFE53935)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (job.status == FoxPostJobStatus.FAILED) {
                                OutlinedButton(onClick = { FoxPostPublishQueue.retry(job.jobId) }) {
                                    Text("重试")
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (job.status != FoxPostJobStatus.DONE) {
                                IconButton(onClick = { FoxPostPublishQueue.cancel(job.jobId) }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
