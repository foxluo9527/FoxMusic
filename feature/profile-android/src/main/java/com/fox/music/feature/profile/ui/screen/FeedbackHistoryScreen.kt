package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.feedback.MusicFeedbackRecord
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.EmptyView
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.profile.viewmodel.FeedbackFilter
import com.fox.music.feature.profile.viewmodel.FeedbackHistoryEffect
import com.fox.music.feature.profile.viewmodel.FeedbackHistoryIntent
import com.fox.music.feature.profile.viewmodel.FeedbackHistoryViewModel

const val FEEDBACK_HISTORY_ROUTE = "settings/feedback_history"

/**
 * 音源反馈记录：我提交过的「音乐不匹配」反馈 + 自动换源进度。
 *
 * 与举报历史同一套 MVI + 分页范式；差异在于每条记录额外展示
 * 自动匹配状态（匹配中 / 已自动换源 / 暂未匹配）与处理说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedbackHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(FeedbackHistoryIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is FeedbackHistoryEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, state.canLoadMore) {
        if (shouldLoadMore && state.canLoadMore) {
            viewModel.sendIntent(FeedbackHistoryIntent.LoadMore)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("音源反馈记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.sendIntent(FeedbackHistoryIntent.Refresh) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterChipsRow(
                current = state.filter,
                onSelect = { viewModel.sendIntent(FeedbackHistoryIntent.ChangeFilter(it)) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.records.isEmpty() -> {
                        LoadingIndicator(useLottie = false)
                    }
                    state.error != null && state.records.isEmpty() -> {
                        ErrorView(
                            message = state.error ?: "加载失败",
                            retryText = "重试",
                            onRetry = { viewModel.sendIntent(FeedbackHistoryIntent.Load) },
                        )
                    }
                    state.records.isEmpty() -> {
                        EmptyView(
                            message = "暂无反馈记录",
                            icon = Icons.Filled.ReportProblem,
                            subtitle = "在歌曲「更多」里反馈音源错误后，进度会显示在这里",
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.records, key = { it.id }) { record ->
                                FeedbackHistoryItem(
                                    record = record,
                                    expanded = state.expandedId == record.id,
                                    onToggle = {
                                        viewModel.sendIntent(
                                            FeedbackHistoryIntent.ToggleExpand(record.id)
                                        )
                                    },
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(current: FeedbackFilter, onSelect: (FeedbackFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedbackFilter.entries.forEach { filter ->
            val selected = filter == current
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { onSelect(filter) },
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedbackHistoryItem(
    record: MusicFeedbackRecord,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CachedImage(
                    imageUrl = record.musicCover,
                    contentDescription = record.musicTitle,
                    modifier = Modifier
                        .size(52.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.musicTitle.ifBlank { "未知歌曲" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = buildString {
                        if (record.musicArtists.isNotBlank()) append(record.musicArtists)
                        val duration = formatDuration(record.musicDuration)
                        if (duration.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(duration)
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 90f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(record)
                AutoRepairChip(record)
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                Spacer(modifier = Modifier.height(10.dp))

                DetailLine(label = "反馈说明", value = record.content?.takeIf { it.isNotBlank() } ?: "未填写")
                // 跨模块 public 属性不能 smart cast，先取局部值
                val autoDetail = record.autoRepairDetail
                if (!autoDetail.isNullOrBlank()) {
                    DetailLine(
                        label = "自动匹配",
                        value = buildString {
                            append(autoDetail)
                            val via = autoRepairViaLabel(record.autoRepairVia)
                            if (via.isNotBlank()) append("（$via）")
                        },
                    )
                }
                val handleNote = record.handleNote
                if (!handleNote.isNullOrBlank()) {
                    DetailLine(label = "处理结果", value = handleNote)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildTimeLine(record),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 人工处理状态 */
@Composable
private fun StatusChip(record: MusicFeedbackRecord) {
    val (label, color) = when (record.status) {
        "pending" -> "待处理" to Color(0xFFF59E0B)
        "resolved" -> "已处理" to Color(0xFF22C55E)
        "rejected" -> "已忽略" to Color(0xFF9CA3AF)
        else -> (record.status.ifBlank { "未知" }) to Color(0xFF9CA3AF)
    }
    MiniChip(label = label, color = color)
}

/** 自动匹配进度（用户最关心的「有没有换到新音源」） */
@Composable
private fun AutoRepairChip(record: MusicFeedbackRecord) {
    val (label, color) = when (record.autoRepairStatus) {
        "running" -> "智能匹配中" to Color(0xFF3B82F6)
        "succeeded" -> "已自动换源" to Color(0xFF22C55E)
        "failed" -> "暂未匹配" to Color(0xFFEF4444)
        "skipped" -> "已跳过" to Color(0xFF9CA3AF)
        else -> if (record.isPending) "排队中" to Color(0xFF9CA3AF) else "" to Color.Unspecified
    }
    if (label.isNotBlank()) MiniChip(label = label, color = color)
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private fun autoRepairViaLabel(via: String?): String = when (via) {
    "ai" -> "AI 识别"
    "legacy" -> "旧竞速链"
    else -> ""
}

private fun buildTimeLine(record: MusicFeedbackRecord): String = buildString {
    val created = formatTime(record.createdAt)
    if (created.isNotBlank()) append("反馈于 $created")
    val handled = formatTime(record.handledAt)
    if (handled.isNotBlank()) {
        if (isNotEmpty()) append(" · ")
        append("处理于 $handled")
    }
}

private fun formatTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.replace("T", " ").take(16)
}

/** 毫秒 → mm:ss（服务端 music.duration 为毫秒，0 表示未知） */
private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
