package com.fox.music.feature.playlist.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.domain.repository.ImportTrackItem
import com.fox.music.core.domain.repository.PlaylistImportDetail
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxDialogField
import com.fox.music.core.ui.component.FoxLinearProgress
import com.fox.music.core.ui.component.FoxMultiInputDialog
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PreviewLyricPanel
import com.fox.music.feature.playlist.viewmodel.PlaylistImportDetailEffect
import com.fox.music.feature.playlist.viewmodel.PlaylistImportDetailIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistImportDetailState
import com.fox.music.feature.playlist.viewmodel.PlaylistImportDetailViewModel
import kotlin.math.abs

const val PLAYLIST_IMPORT_DETAIL_ROUTE = "playlist_import_detail/{playlistId}"

fun playlistImportDetailRoute(playlistId: Long): String =
    "playlist_import_detail/$playlistId"

/**
 * 歌单导入详情：逐首上传状态一览；失败曲目可手动选择音源（与音源纠错同一条链路）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaylistImportDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCancelConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PlaylistImportDetailEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                PlaylistImportDetailEffect.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("导入详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val detail = state.detail
                    if (detail != null && detail.failed > 0) {
                        TextButton(
                            onClick = { viewModel.sendIntent(PlaylistImportDetailIntent.RetryAll) },
                        ) { Text("全部重试") }
                    }
                    if (detail != null && !detail.isFinished) {
                        TextButton(onClick = { showCancelConfirm = true }) { Text("取消") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }

            state.detail == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有正在进行的导入任务",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> ImportDetailList(
                detail = state.detail!!,
                onRetryTrack = {
                    viewModel.sendIntent(PlaylistImportDetailIntent.RetryTrack(it))
                },
                onPickSource = {
                    viewModel.sendIntent(PlaylistImportDetailIntent.OpenSourcePicker(it))
                },
                onResume = { viewModel.sendIntent(PlaylistImportDetailIntent.ResumeJob) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }

    if (showCancelConfirm) {
        FoxDialog(
            title = "取消剩余导入",
            text = "将清除未完成曲目的导入记录，已导入的歌曲不受影响。",
            confirmText = "确认取消",
            dismissText = "再想想",
            onDismiss = { showCancelConfirm = false },
            onConfirm = {
                showCancelConfirm = false
                viewModel.sendIntent(PlaylistImportDetailIntent.CancelJob)
            },
            destructive = true,
        )
    }

    state.pickerTrack?.let { track ->
        SourcePickerSheet(
            state = state,
            track = track,
            onDismiss = { viewModel.sendIntent(PlaylistImportDetailIntent.CloseSourcePicker) },
            onSearch = { viewModel.sendIntent(PlaylistImportDetailIntent.SearchProviders) },
            onEditQuery = { title, artist ->
                viewModel.sendIntent(PlaylistImportDetailIntent.EditQuery(title, artist))
            },
            onSelectGroup = {
                viewModel.sendIntent(PlaylistImportDetailIntent.SelectGroup(it))
            },
            onSelectCandidate = {
                viewModel.sendIntent(PlaylistImportDetailIntent.SelectCandidate(it))
            },
            onTogglePlay = {
                viewModel.sendIntent(PlaylistImportDetailIntent.ToggleCandidatePlay(it))
            },
            onTogglePreviewPlayPause = {
                viewModel.sendIntent(PlaylistImportDetailIntent.TogglePreviewPlayPause)
            },
            onSeekPreview = {
                viewModel.sendIntent(PlaylistImportDetailIntent.SeekPreview(it))
            },
            onStopPreview = {
                viewModel.sendIntent(PlaylistImportDetailIntent.StopPreview)
            },
            onApply = { viewModel.sendIntent(PlaylistImportDetailIntent.ApplySelected) },
        )
    }
}

@Composable
private fun ImportDetailList(
    detail: PlaylistImportDetail,
    onRetryTrack: (String) -> Unit,
    onPickSource: (String) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val processed = detail.done + detail.failed
    val fraction = if (detail.total > 0) {
        (processed.toFloat() / detail.total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "summary") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = detail.playlistTitle.ifBlank { "歌单导入" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // needs_retry 批次只剩失败项（已导入记录被清理），此时报「剩余」更准确
                    text = if (detail.status == "needs_retry") {
                        "剩余 ${detail.failed} 首导入失败"
                    } else {
                        buildString {
                            append("已导入 ${detail.done}/${detail.total}")
                            if (detail.failed > 0) append("　失败 ${detail.failed}")
                            if (detail.remaining > 0) append("　待处理 ${detail.remaining}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FoxLinearProgress(
                    progress = fraction,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (detail.status == "paused") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "任务已暂停",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onResume) { Text("继续导入") }
                    }
                }
            }
            HorizontalDivider()
        }

        items(detail.tracks, key = { it.trackJobId }) { track ->
            ImportTrackRow(
                track = track,
                onRetry = { onRetryTrack(track.trackJobId) },
                onPickSource = { onPickSource(track.trackJobId) },
            )
        }

        if (detail.failed == 0 && detail.remaining == 0) {
            item(key = "all_done") {
                Text(
                    text = "全部曲目导入完成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ImportTrackRow(
    track: ImportTrackItem,
    onRetry: () -> Unit,
    onPickSource: () -> Unit,
) {
    val failed = track.status == "failed"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (failed) Modifier.clickable(onClick = onPickSource) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists.ifBlank { "未知艺人" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (failed) {
                Text(
                    text = track.error?.takeIf { it.isNotBlank() } ?: "解析失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "点按手动选择音源",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = statusLabel(track.status),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(track.status),
            )
            if (failed) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "重试该曲目",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "failed" -> MaterialTheme.colorScheme.error
    "completed" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "已导入"
    "failed" -> "失败"
    "running" -> "导入中"
    "paused" -> "已暂停"
    else -> "排队中"
}

/**
 * 音源选择面板：候选来自与音源纠错相同的多源搜索/解析接口，
 * 确认后把该音源转存、入库并加回歌单。
 *
 * 高度与展开完全交给 M3 的 ModalBottomSheet：`skipPartiallyExpanded = false` 时系统
 * 自带「半屏 / 全展开」两档与拖拽把手，不再自绘——自绘会把把手叠成两条（系统一条、
 * 我们的再一条）。任意 60%/80% 比例系统不支持，按约定不做。
 *
 * 试听区与音源纠错同一套（core:ui 的 PreviewLyricPanel）：可拖进度条 + 原曲歌词跟随，
 * 用来对照「听到的」和「这首歌本该是什么」是否一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    state: PlaylistImportDetailState,
    track: ImportTrackItem,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onEditQuery: (String, String) -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectCandidate: (ProviderCandidate) -> Unit,
    onTogglePlay: (ProviderCandidate) -> Unit,
    onTogglePreviewPlayPause: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onStopPreview: () -> Unit,
    onApply: () -> Unit,
) {
    // 系统两档展开：半屏 ↔ 全展开，把手也用系统 dragHandle
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showQueryEditor by remember { mutableStateOf(false) }

    FoxSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 给系统一个足够高的内容：半屏档约等于半屏，上拖铺到 92%
                .fillMaxHeight(0.92f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "为「${track.title}」选择音源",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artists.ifBlank { "未知艺人" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildString {
                                append(state.query.ifBlank { track.title })
                                if (state.artistQuery.isNotBlank()) {
                                    append(" · ")
                                    append(state.artistQuery)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showQueryEditor = true }) { Text("编辑") }
                        Button(onClick = onSearch, enabled = !state.isSearching) {
                            Text(if (state.isSearching) "搜索中" else "搜索")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (state.groups.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = state.groups
                        .indexOfFirst { it.key == state.activeGroup?.key }
                        .coerceAtLeast(0),
                    edgePadding = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                    divider = {},
                ) {
                    state.groups.forEach { group ->
                        Tab(
                            selected = state.activeGroup?.key == group.key,
                            onClick = { onSelectGroup(group.key) },
                            text = { Text(group.label, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isSearching -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "正在搜索各音源…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.sortedCandidates.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "该音源没有候选结果，可修改搜索条件再试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(
                            items = state.sortedCandidates,
                            key = { it.id },
                        ) { candidate ->
                            val resolved = state.resolvedUrls.containsKey(candidate.id) ||
                                candidate.playType == CandidatePlayType.DIRECT
                            SourceCandidateRow(
                                candidate = candidate,
                                selected = state.selectedCandidateId == candidate.id,
                                resolved = resolved,
                                isResolving = state.resolvingIds.contains(candidate.id),
                                isPlaying = state.previewCandidateId == candidate.id &&
                                    state.isPreviewPlaying,
                                targetDurationMs = track.durationMs,
                                onSelect = { onSelectCandidate(candidate) },
                                onTogglePlay = { onTogglePlay(candidate) },
                            )
                        }
                        item(key = "footer") {
                            Text(
                                text = "共 ${state.sortedCandidates.size} 条结果（同艺人/时长接近的排前面）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // 试听区（与音源纠错同一套展示）：进度条可拖、歌词跟随，便于判断版本是否一致
            if (state.previewCandidateId != null) {
                val playing = state.selectedCandidate?.takeIf { it.id == state.previewCandidateId }
                    ?: state.sortedCandidates.firstOrNull { it.id == state.previewCandidateId }
                PreviewLyricPanel(
                    title = playing?.title ?: track.title,
                    artist = playing?.artist.orEmpty().ifBlank { track.artists },
                    positionMs = state.previewPositionMs,
                    durationMs = state.previewDurationMs,
                    isPlaying = state.isPreviewPlaying,
                    lyrics = state.trackLyrics,
                    onPlayPause = onTogglePreviewPlayPause,
                    onSeek = onSeekPreview,
                    onClose = onStopPreview,
                    headerText = if (state.isLyricsLoading) {
                        "正在加载原曲歌词…"
                    } else {
                        "原曲歌词 · 对照试听"
                    },
                    emptyLyricText = "该曲目暂无可用歌词，可凭时长/听感判断",
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        state.selectedCandidate == null -> "请选择一首候选音源"
                        state.selectedResolvedUrl != null ->
                            "已解析：${state.selectedCandidate?.title.orEmpty()}"
                        else -> "正在解析音源…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApply, enabled = state.canApply) {
                    Text(if (state.applying) "导入中…" else "用此音源导入")
                }
            }
        }
    }

    if (showQueryEditor) {
        FoxMultiInputDialog(
            title = "搜索条件",
            confirmText = "重新搜索",
            fields = listOf(
                FoxDialogField(label = "歌曲名称", initialValue = state.query),
                FoxDialogField(label = "艺人（可留空）", initialValue = state.artistQuery),
            ),
            onDismiss = { showQueryEditor = false },
            onConfirm = { values ->
                showQueryEditor = false
                onEditQuery(values[0].trim(), values[1].trim())
            },
        )
    }
}

@Composable
private fun SourceCandidateRow(
    candidate: ProviderCandidate,
    selected: Boolean,
    resolved: Boolean,
    isResolving: Boolean,
    isPlaying: Boolean,
    targetDurationMs: Long,
    onSelect: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    // 时长与歌单标注差距过大时提示（版本不符/试听片段的常见信号）
    val durationDiff = if (targetDurationMs > 0 && (candidate.durationMs ?: 0L) > 0) {
        abs((candidate.durationMs ?: 0L) - targetDurationMs)
    } else {
        0L
    }
    val durationWarning = targetDurationMs > 0 && durationDiff > 5_000
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = candidate.canSelect,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildString {
                if (candidate.artist.isNotBlank()) append(candidate.artist)
                val duration = candidate.durationMs?.takeIf { it > 0 }
                if (duration != null) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDuration(duration))
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (durationWarning) {
                Text(
                    text = "时长与歌单不一致（${formatDuration(targetDurationMs)}）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            isResolving -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            resolved -> IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停试听" else "试听",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            candidate.canSelect -> TextButton(onClick = onTogglePlay) { Text("解析") }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
