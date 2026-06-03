package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.report.Report
import com.fox.music.core.ui.component.EmptyView
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.profile.viewmodel.ReportHistoryEffect
import com.fox.music.feature.profile.viewmodel.ReportHistoryIntent
import com.fox.music.feature.profile.viewmodel.ReportHistoryViewModel

const val REPORT_HISTORY_ROUTE = "settings/report_history"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(ReportHistoryIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReportHistoryEffect.ShowMessage ->
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
            viewModel.sendIntent(ReportHistoryIntent.LoadMore)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("举报历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.reports.isEmpty() -> {
                    LoadingIndicator(useLottie = false)
                }
                state.error != null && state.reports.isEmpty() -> {
                    ErrorView(
                        message = state.error ?: "加载失败",
                        retryText = "重试",
                        onRetry = { viewModel.sendIntent(ReportHistoryIntent.Load) },
                    )
                }
                state.reports.isEmpty() -> {
                    EmptyView(
                        message = "暂无举报记录",
                        icon = Icons.Filled.Flag,
                        subtitle = "你提交的举报会显示在这里",
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.reports, key = { it.id }) { report ->
                            ReportHistoryItem(report = report)
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

@Composable
private fun ReportHistoryItem(report: Report) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = targetTypeLabel(report.targetType),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ReportStatusChip(status = report.status)
            }
            Spacer4()
            Text(
                text = "原因：${reasonLabel(report.reason)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val description = report.description
            if (!description.isNullOrBlank()) {
                Spacer4()
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            val time = formatReportTime(report.createdAt)
            if (time.isNotBlank()) {
                Spacer4()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer4()
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun Spacer4() {
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun ReportStatusChip(status: String) {
    val (label, color) = statusLabelAndColor(status)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun targetTypeLabel(type: String): String = when (type) {
    "post" -> "动态"
    "comment" -> "评论"
    "user" -> "用户"
    "music" -> "歌曲"
    "novel" -> "小说"
    "video" -> "视频"
    else -> "举报"
}

private fun reasonLabel(reason: String): String = when (reason) {
    "spam" -> "垃圾信息"
    "abuse" -> "辱骂攻击"
    "porn" -> "色情低俗"
    "copyright" -> "侵权盗版"
    "illegal" -> "违法违规"
    "other" -> "其他"
    else -> reason.ifBlank { "其他" }
}

private fun statusLabelAndColor(status: String): Pair<String, Color> = when (status) {
    "pending" -> "待处理" to Color(0xFFF59E0B)
    "processing" -> "处理中" to Color(0xFF3B82F6)
    "resolved" -> "已处理" to Color(0xFF22C55E)
    "rejected" -> "已驳回" to Color(0xFFEF4444)
    else -> (status.ifBlank { "未知" }) to Color(0xFF9CA3AF)
}

private fun formatReportTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.replace("T", " ").take(16)
}
