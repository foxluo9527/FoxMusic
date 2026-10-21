package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PreviewLyricPanel
import com.fox.music.feature.player.viewmodel.PreviewPlaybackState
import com.fox.music.feature.player.viewmodel.SourceCorrectEffect
import com.fox.music.feature.player.viewmodel.SourceCorrectIntent
import com.fox.music.feature.player.viewmodel.SourceCorrectMode
import com.fox.music.feature.player.viewmodel.SourceCorrectViewModel

const val SOURCE_CORRECT_ROUTE = "source_correct/{musicId}"

fun sourceCorrectRoute(musicId: Long): String = "source_correct/$musicId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceCorrectScreen(
    musicId: Long,
    modifier: Modifier = Modifier,
    viewModel: SourceCorrectViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onReplaceSucceeded: () -> Unit = onBack,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showReplaceConfirm by remember { mutableStateOf(false) }
    var showQueryEditor by remember { mutableStateOf(false) }

    LaunchedEffect(musicId) {
        viewModel.sendIntent(SourceCorrectIntent.Init(musicId))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SourceCorrectEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                SourceCorrectEffect.ReplaceSucceeded -> onReplaceSucceeded()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("音源纠错") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 右上角切换「搜索替换 / 上传文件」模式（不再用 tab）
                    TextButton(
                        onClick = {
                            val next = if (state.mode == SourceCorrectMode.SEARCH) {
                                SourceCorrectMode.UPLOAD
                            } else {
                                SourceCorrectMode.SEARCH
                            }
                            viewModel.sendIntent(SourceCorrectIntent.SwitchMode(next))
                        },
                    ) {
                        Text(
                            if (state.mode == SourceCorrectMode.SEARCH) "上传文件" else "搜索替换",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (state.mode == SourceCorrectMode.SEARCH) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    state.resolvedCandidate != null ->
                                        "已选并解析：${state.resolvedCandidate?.title}"
                                    state.selectedCandidate != null ->
                                        "已选：${state.selectedCandidate?.title}（解析中…）"
                                    else -> "请选择一首候选歌曲（点击自动解析音源）"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = { showReplaceConfirm = true },
                            enabled = state.resolvedCandidate != null && !state.isReplacing,
                        ) {
                            Text(if (state.isReplacing) "替换中..." else "确认替换")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SourceCorrectHeader(
                    music = state.targetMusic,
                    isLoading = state.isTargetLoading,
                )

                when (state.mode) {
                    SourceCorrectMode.SEARCH -> SearchReplaceTab(
                        query = state.query,
                        artistQuery = state.artistQuery,
                        isSearching = state.isSearching,
                        groups = state.groups,
                        activeGroupKey = state.activeGroup?.key.orEmpty(),
                        candidates = state.activeCandidates,
                        selectedCandidateId = state.selectedCandidateId,
                        resolvingIds = state.resolvingIds,
                        resolvedUrls = state.resolvedUrls,
                        previewCandidateId = state.previewCandidateId,
                        isPreviewPlaying = state.preview.isPlaying,
                        targetDurationMs = state.targetMusic?.duration,
                        onEditQuery = { showQueryEditor = true },
                        onSearch = { viewModel.sendIntent(SourceCorrectIntent.SearchProviders) },
                        onSelectGroup = { viewModel.sendIntent(SourceCorrectIntent.SelectGroup(it)) },
                        onSelectCandidate = {
                            viewModel.sendIntent(SourceCorrectIntent.SelectCandidate(it))
                        },
                        onTogglePlay = {
                            viewModel.sendIntent(SourceCorrectIntent.ToggleCandidatePlay(it))
                        },
                    )

                    SourceCorrectMode.UPLOAD -> UploadReplaceTab(
                        pickedFileName = state.pickedFileName,
                        isReplacing = state.isReplacing,
                        onFilePicked = { uri, name ->
                            viewModel.sendIntent(SourceCorrectIntent.PickFile(uri, name))
                        },
                        onReplace = { viewModel.sendIntent(SourceCorrectIntent.ReplaceFromFile) },
                    )
                }
            }

            state.preview.music?.let { previewMusic ->
                PreviewLyricPanel(
                    title = previewMusic.title,
                    artist = previewMusic.artists.joinToString(", ") { it.name },
                    positionMs = state.preview.positionMs,
                    durationMs = state.preview.durationMs,
                    isPlaying = state.preview.isPlaying,
                    lyrics = state.targetLyrics,
                    onPlayPause = { viewModel.sendIntent(SourceCorrectIntent.TogglePreviewPlayPause) },
                    onSeek = { viewModel.sendIntent(SourceCorrectIntent.SeekPreview(it)) },
                    onClose = { viewModel.sendIntent(SourceCorrectIntent.StopPreview) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showQueryEditor) {
        QueryEditorDialog(
            initialTitle = state.query,
            initialArtist = state.artistQuery,
            onDismiss = { showQueryEditor = false },
            onConfirm = { title, artist ->
                showQueryEditor = false
                viewModel.sendIntent(SourceCorrectIntent.EditQuery(title, artist))
            },
        )
    }

    if (showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("确认替换音源") },
            text = {
                Text(
                    "将把「${state.targetMusic?.title ?: "当前歌曲"}」的音源替换为「${state.resolvedCandidate?.title ?: ""}」，替换后会自动重新分析高潮片段。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplaceConfirm = false
                        viewModel.sendIntent(SourceCorrectIntent.ReplaceFromThirdParty)
                    },
                ) {
                    Text("确认替换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SourceCorrectHeader(
    music: Music?,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = music?.coverImage,
            contentDescription = music?.title,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music?.title ?: "加载中...",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music?.artists?.joinToString(", ") { it.name }?.ifBlank { "未知歌手" }
                    ?: if (isLoading) "正在获取歌曲信息" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "当前音源",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 单行搜索区：名称 · 艺人 + 编辑 + 搜索按钮。
 * 用户 2026-10-21：「搜索词区的高度减少一点」——名称与艺人合并为单行展示。
 */
@Composable
internal fun SearchQueryBar(
    query: String,
    artistQuery: String,
    isSearching: Boolean,
    onEditQuery: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = queryDisplayText(query, artistQuery),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditQuery() }
                    .padding(end = 8.dp),
            )
            TextButton(onClick = onEditQuery) {
                Text("编辑")
            }
            Button(onClick = onSearch, enabled = !isSearching) {
                Text(if (isSearching) "搜索中" else "搜索")
            }
        }
    }
}

/** 单行展示「名称 · 艺人」；名称为空时展示占位符，艺人可留空 */
private fun queryDisplayText(query: String, artistQuery: String): String {
    val name = query.ifBlank { "歌曲名称" }
    val artist = artistQuery.trim()
    return if (artist.isBlank()) name else "$name · $artist"
}

@Composable
private fun SearchReplaceTab(
    query: String,
    artistQuery: String,
    isSearching: Boolean,
    groups: List<com.fox.music.core.model.music.ProviderCandidateGroup>,
    activeGroupKey: String,
    candidates: List<ProviderCandidate>,
    selectedCandidateId: String?,
    resolvingIds: Set<String>,
    resolvedUrls: Map<String, String>,
    previewCandidateId: String?,
    isPreviewPlaying: Boolean,
    targetDurationMs: Long?,
    onEditQuery: () -> Unit,
    onSearch: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectCandidate: (ProviderCandidate) -> Unit,
    onTogglePlay: (ProviderCandidate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchQueryBar(
            query = query,
            artistQuery = artistQuery,
            isSearching = isSearching,
            onEditQuery = onEditQuery,
            onSearch = onSearch,
        )

        if (groups.isNotEmpty()) {
            // 用户 2026-10-21：tab 应可超出屏幕、单行文字、左右滑动查看；
            // 数量不放 tab，改在列表 footer 展示
            ScrollableTabRow(
                selectedTabIndex = groups.indexOfFirst { it.key == activeGroupKey }.coerceAtLeast(0),
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth(),
                divider = {},
            ) {
                groups.forEach { group ->
                    Tab(
                        selected = activeGroupKey == group.key,
                        onClick = { onSelectGroup(group.key) },
                        text = { Text(group.label, maxLines = 1) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            groups.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "从各音源渠道搜索候选，勾选后自动解析音源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            candidates.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "该音源没有候选结果",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                ) {
                    itemsIndexed(
                        items = candidates,
                        key = { index, candidate -> "${index}_${candidate.id}" },
                    ) { _, candidate ->
                        val resolved = resolvedUrls.containsKey(candidate.id) ||
                            candidate.playType == CandidatePlayType.DIRECT
                        val isPreviewing = previewCandidateId == candidate.id
                        CandidateRow(
                            candidate = candidate,
                            selected = selectedCandidateId == candidate.id,
                            resolved = resolved,
                            isResolving = resolvingIds.contains(candidate.id),
                            isPreviewing = isPreviewing && isPreviewPlaying,
                            targetDurationMs = targetDurationMs,
                            onSelect = { onSelectCandidate(candidate) },
                            onTogglePlay = { onTogglePlay(candidate) },
                        )
                    }
                    // 渠道候选数量统一在 footer 展示（不放 tab）
                    item(key = "channel_count_footer") {
                        Text(
                            text = "共 ${candidates.size} 条结果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 搜索条件编辑弹窗（纠错/收录共用） */
@Composable
internal fun QueryEditorDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索条件") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("歌曲名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("艺人（可留空，宽松匹配）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), artist.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text("搜索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 候选行：单选 + 标题/艺人 + 右侧解析/播放图标（纠错/收录共用） */
@Composable
internal fun CandidateRow(
    candidate: ProviderCandidate,
    selected: Boolean,
    resolved: Boolean,
    isResolving: Boolean,
    isPreviewing: Boolean,
    targetDurationMs: Long?,
    onSelect: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    val canSelect = candidate.canSelect
    val sameAsCurrent = targetDurationMs != null &&
        targetDurationMs > 0 &&
        (candidate.durationMs ?: 0L) > 0 &&
        kotlin.math.abs((candidate.durationMs ?: 0L) - targetDurationMs) < 3_000
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = canSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                // 用户 2026-10-21：网页解析标题字号减小
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (candidate.artist.isNotBlank()) {
                Text(
                    text = candidate.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sameAsCurrent) {
                Text(
                    text = "⚠ 疑似当前音源（时长几乎一致，替换后内容可能不变）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            isResolving -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            // 官方 API 直链 / 已解析成功：播放图标（图标，非「试听」文字）
            resolved -> IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPreviewing) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // hifini 等需逆向：展示「解析」，勾选或点击均可触发
            canSelect -> TextButton(onClick = onTogglePlay) {
                Text("解析")
            }
        }
    }
}

@Composable
private fun UploadReplaceTab(
    pickedFileName: String?,
    isReplacing: Boolean,
    onFilePicked: (android.net.Uri?, String?) -> Unit,
    onReplace: () -> Unit,
) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri)
            onFilePicked(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "从本地选择音频文件，上传后替换当前歌曲的音源。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { pickerLauncher.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (pickedFileName != null) "重新选择文件" else "选择音频文件")
        }

        if (pickedFileName != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "已选择：$pickedFileName",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onReplace,
            enabled = pickedFileName != null && !isReplacing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isReplacing) "替换中..." else "确认替换")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "支持 mp3 / flac / wav / m4a / aac 等常见音频格式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    }
}

internal fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
