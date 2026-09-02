package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.domain.repository.PostPublishJob
import com.fox.music.core.domain.repository.PostPublishJobStatus
import com.fox.music.core.ui.component.EmptyView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.social.viewmodel.PostPublishQueueEffect
import com.fox.music.feature.social.viewmodel.PostPublishQueueIntent
import com.fox.music.feature.social.viewmodel.PostPublishQueueViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val POST_PUBLISH_QUEUE_ROUTE = "post_publish_queue"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostPublishQueueScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: PostPublishQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PostPublishQueueEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("任务状态") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.jobs.isEmpty() -> LoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            state.jobs.isEmpty() -> EmptyView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = "暂无上传任务",
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(state.jobs, key = { it.jobId }) { job ->
                    PostPublishJobCard(
                        job = job,
                        onRetry = { viewModel.sendIntent(PostPublishQueueIntent.Retry(job.jobId)) },
                        onCancel = { viewModel.sendIntent(PostPublishQueueIntent.Cancel(job.jobId)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostPublishJobCard(
    job: PostPublishJob,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val preview = job.title?.takeIf { it.isNotBlank() }
        ?: job.content.take(40).ifBlank { "无标题帖子" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = preview,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(PostPublishQueueViewModel.statusLabel(job.status))
                if (job.totalLocalUploadCount > 0) {
                    append(" · 媒体 ")
                    append(job.uploadedLocalCount)
                    append("/")
                    append(job.totalLocalUploadCount)
                }
                append(" · ")
                append(formatter.format(Date(job.createdAt)))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        job.errorMessage?.takeIf { job.status == PostPublishJobStatus.FAILED }?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("取消")
            }
            if (job.status == PostPublishJobStatus.FAILED) {
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
    }
}
