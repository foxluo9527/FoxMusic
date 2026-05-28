package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.profile.viewmodel.DownloadEffect
import com.fox.music.feature.profile.viewmodel.DownloadViewModel

const val DOWNLOAD_MANAGER_ROUTE = "download_manager"

@Composable
private fun RowScope.DownloadToolbarTextButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    TextButton(modifier = Modifier.weight(1f),onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
) {
    val downloads by viewModel.downloads.collectAsState()
    val completedDownloads by viewModel.completedDownloads.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                DownloadEffect.NavigateToPlayer -> onNavigateToPlayer()
                is DownloadEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无下载任务",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (completedDownloads.isNotEmpty()) {
                            DownloadToolbarTextButton(
                                onClick = viewModel::playAllCompleted,
                                icon = Icons.Default.PlaylistPlay,
                                label = "播放全部",
                            )
                        }
                        DownloadToolbarTextButton(
                            onClick = viewModel::pauseAll,
                            icon = Icons.Default.Pause,
                            label = "全部暂停",
                        )
                        DownloadToolbarTextButton(
                            onClick = viewModel::resumeAll,
                            icon = Icons.Default.PlayArrow,
                            label = "全部继续",
                        )
                    }
                }
                item {
                    Text(
                        text = "已用存储 ${viewModel.formatBytes(viewModel.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(downloads, key = { it.musicId }) { task ->
                    DownloadTaskItem(
                        task = task,
                        statusLabel = viewModel.statusLabel(task),
                        onClick = {
                            if (task.status == DownloadStatus.COMPLETED) {
                                viewModel.playDownload(task)
                            }
                        },
                        onPlay = { viewModel.playDownload(task) },
                        onPause = { viewModel.pause(task.musicId) },
                        onResume = { viewModel.resume(task.musicId) },
                        onDelete = { viewModel.delete(task.musicId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    statusLabel: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPlayable = task.status == DownloadStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPlayable) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = task.coverUrl,
            contentDescription = task.title,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.artistNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (task.status == DownloadStatus.DOWNLOADING) {
                val progressFraction = when {
                    task.totalBytes > 0L ->
                        (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
                    task.progress > 0 -> task.progress / 100f
                    else -> null
                }
                if (progressFraction != null) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        strokeCap = StrokeCap.Butt,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        when (task.status) {
            DownloadStatus.COMPLETED -> {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "播放")
                }
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                IconButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = "暂停")
                }
            }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                IconButton(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "继续下载")
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}
