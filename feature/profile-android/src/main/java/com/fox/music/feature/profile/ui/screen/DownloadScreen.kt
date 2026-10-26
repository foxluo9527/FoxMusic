package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.DownloadActionBottomSheet
import com.fox.music.core.ui.component.FoxIndeterminateLinearProgress
import com.fox.music.core.ui.component.FoxLinearProgress
import com.fox.music.core.ui.component.JumpToPlayingFabForList
import com.fox.music.core.ui.component.LocalJumpHighlight
import com.fox.music.core.ui.component.LocalNowPlayingMusicId
import com.fox.music.core.ui.component.jumpHighlight
import com.fox.music.core.ui.theme.CoverShapeRail
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.feature.profile.viewmodel.DownloadEffect
import com.fox.music.feature.profile.viewmodel.DownloadViewModel
import kotlinx.coroutines.launch

const val DOWNLOAD_MANAGER_ROUTE = "download_manager"

@Composable
private fun RowScope.DownloadToolbarTextButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(17.dp),
            tint = scheme.primary,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
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
    val actionTask by viewModel.actionTask.collectAsState()
    val showActionSheet by viewModel.showActionSheet.collectAsState()
    val totalBytes by viewModel.totalBytes.collectAsState()
    val context = LocalContext.current
    // 正在播放（app 层 provide）：用于高亮 + 「跳到正在播放」
    val nowPlayingId = LocalNowPlayingMusicId.current
    val downloadListState = rememberLazyListState()
    val jumpScope = rememberCoroutineScope()
    val jumpHighlight = LocalJumpHighlight.current
    // 列表头部两个 item（批量操作卡 + 存储提示），任务从索引 2 开始
    val playingTaskIndex =
        if (nowPlayingId == null) -1 else downloads.indexOfFirst { it.musicId == nowPlayingId }
    val playingLazyIndex = if (playingTaskIndex >= 0) playingTaskIndex + 2 else -1

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
        // 底色由 MainScreenWithBottomBar 的主题渐变提供，透明才能看到主题色顶光。
        containerColor = Color.Transparent,
        topBar = {
            FoxTopBar(
                title = "下载管理",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        floatingActionButton = {
            JumpToPlayingFabForList(
                listState = downloadListState,
                targetIndex = playingLazyIndex,
                onClick = {
                    jumpScope.launch {
                        downloadListState.animateScrollToItem(playingLazyIndex)
                        // 到位后再闪一下，强调「就是这一行」
                        jumpHighlight.flash(nowPlayingId)
                    }
                },
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
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = downloadListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = FoxSpacing.pagePadding,
                    end = FoxSpacing.pagePadding,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 批量操作：收进一张卡片，三段等分 + 发丝线分隔（原来是三个裸 TextButton）
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, PaperHairline, RoundedCornerShape(14.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (completedDownloads.isNotEmpty()) {
                            DownloadToolbarTextButton(
                                onClick = viewModel::playAllCompleted,
                                icon = Icons.Default.PlaylistPlay,
                                label = "播放全部",
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp),
                                color = PaperHairline,
                            )
                        }
                        DownloadToolbarTextButton(
                            onClick = viewModel::pauseAll,
                            icon = Icons.Default.Pause,
                            label = "全部暂停",
                        )
                        HorizontalDivider(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp),
                            color = PaperHairline,
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
                        text = "已用存储 ${viewModel.formatBytes(totalBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                items(downloads, key = { it.musicId }) { task ->
                    DownloadTaskItem(
                        task = task,
                        statusLabel = viewModel.statusLabel(task),
                        isNowPlaying = task.musicId == nowPlayingId,
                        onClick = {
                            when (task.status) {
                                DownloadStatus.COMPLETED -> viewModel.playDownload(task)
                                DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                                    viewModel.resume(task.musicId)

                                DownloadStatus.PENDING, DownloadStatus.DOWNLOADING ->
                                    viewModel.pause(task.musicId)
                            }
                        },
                        onActionClick = {
                            when (task.status) {
                                DownloadStatus.COMPLETED -> viewModel.playDownload(task)
                                DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                                    viewModel.resume(task.musicId)

                                DownloadStatus.PENDING, DownloadStatus.DOWNLOADING ->
                                    viewModel.pause(task.musicId)
                            }
                        },
                        onMoreClick = { viewModel.showMoreActions(task) },
                    )
                }
            }
        }
    }

    if (showActionSheet && actionTask != null) {
        val task = actionTask!!
        DownloadActionBottomSheet(
            task = task,
            onDismiss = viewModel::dismissActionSheet,
            onPlayNext = { viewModel.playNext(task) },
            onDelete = { viewModel.delete(task.musicId) },
            onPause = { viewModel.pause(task.musicId) },
            onResume = { viewModel.resume(task.musicId) },
        )
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    statusLabel: String,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onMoreClick: () -> Unit,
    isNowPlaying: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // v12：已完成的任务不再给「播放」动作键。
    // 播放是「整行点击 → 进播放器」的职责，行尾再放一颗 ▶ 会和「继续下载」的 ▶
    // 长得一模一样（同一颗图标、同一个位置），用户根本分不清点下去是播还是下。
    // 所以已完成 = 没有动作键，只留右侧 ⋮。
    val actionIcon: ImageVector? = when (task.status) {
        DownloadStatus.COMPLETED -> null
        DownloadStatus.PAUSED -> Icons.Default.PlayArrow
        DownloadStatus.FAILED -> Icons.Default.Refresh
        DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> Icons.Default.Pause
    }
    val actionDescription: String? = when (task.status) {
        DownloadStatus.COMPLETED -> null
        DownloadStatus.PAUSED -> "继续下载"
        DownloadStatus.FAILED -> "重新下载"
        DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> "暂停下载"
    }
    val showProgress = task.status == DownloadStatus.DOWNLOADING ||
            (task.status == DownloadStatus.PAUSED && task.downloadedBytes > 0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .border(1.dp, PaperHairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .jumpHighlight(task.musicId)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = task.coverUrl,
            contentDescription = task.title,
            modifier = Modifier.size(48.dp),
            shape = CoverShapeRail,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isNowPlaying) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.artistNames,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = statusLabel,
                fontSize = 11.5.sp,
                color = if (task.status == DownloadStatus.FAILED) {
                    scheme.error
                } else {
                    scheme.primary
                },
            )
            if (showProgress) {
                val progressFraction = when {
                    task.totalBytes > 0L ->
                        (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(
                            0f,
                            1f
                        )

                    task.progress > 0 -> task.progress / 100f
                    else -> null
                }
                if (progressFraction != null) {
                    FoxLinearProgress(
                        progress = { progressFraction },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else if (task.status == DownloadStatus.DOWNLOADING) {
                    FoxIndeterminateLinearProgress(
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        actionIcon?.let { icon ->
            IconButton(onClick = onActionClick) {
                Icon(
                    icon,
                    contentDescription = actionDescription,
                    tint = scheme.primary,
                )
            }
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}
