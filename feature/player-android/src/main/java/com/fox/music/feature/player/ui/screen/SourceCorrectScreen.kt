package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxLinearProgress
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.player.viewmodel.ManageSearchPlatform
import com.fox.music.feature.player.viewmodel.PreviewPlaybackState
import com.fox.music.feature.player.viewmodel.SourceCorrectEffect
import com.fox.music.feature.player.viewmodel.SourceCorrectIntent
import com.fox.music.feature.player.viewmodel.SourceCorrectTab
import com.fox.music.feature.player.viewmodel.SourceCorrectViewModel
import kotlinx.coroutines.flow.Flow

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state.activeTab) {
                                SourceCorrectTab.SEARCH ->
                                    state.selectedThirdParty?.let { "已选：${it.title}" } ?: "请选择一首替换歌曲"
                                SourceCorrectTab.UPLOAD ->
                                    state.pickedFileName?.let { "已选文件：$it" } ?: "请选择音频文件"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { showReplaceConfirm = true },
                        enabled = when (state.activeTab) {
                            SourceCorrectTab.SEARCH -> state.selectedThirdParty != null
                            SourceCorrectTab.UPLOAD -> state.pickedFileUri != null
                        } && !state.isReplacing,
                    ) {
                        Text(if (state.isReplacing) "替换中..." else "确认替换")
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

                TabRow(selectedTabIndex = state.activeTab.ordinal) {
                    SourceCorrectTab.entries.forEach { tab ->
                        Tab(
                            selected = state.activeTab == tab,
                            onClick = { viewModel.sendIntent(SourceCorrectIntent.SelectTab(tab)) },
                            text = { Text(tab.label) },
                        )
                    }
                }

                when (state.activeTab) {
                    SourceCorrectTab.SEARCH -> SearchReplaceTab(
                        query = state.query,
                        platform = state.platform,
                        hasSearched = state.hasSearched,
                        musicPaging = state.musicPaging,
                        searchKey = state.searchKey,
                        selectedMusic = state.selectedThirdParty,
                        targetDurationMs = state.targetMusic?.duration,
                        onQueryChange = { viewModel.sendIntent(SourceCorrectIntent.QueryChange(it)) },
                        onSearch = { viewModel.sendIntent(SourceCorrectIntent.Search) },
                        onSelectPlatform = { viewModel.sendIntent(SourceCorrectIntent.SelectPlatform(it)) },
                        onSelectMusic = {
                            viewModel.sendIntent(SourceCorrectIntent.SelectThirdParty(it))
                        },
                        onPreview = { viewModel.sendIntent(SourceCorrectIntent.Preview(it)) },
                    )

                    SourceCorrectTab.UPLOAD -> UploadReplaceTab(
                        pickedFileName = state.pickedFileName,
                        onFilePicked = { uri, name ->
                            viewModel.sendIntent(SourceCorrectIntent.PickFile(uri, name))
                        },
                    )
                }
            }

            state.preview.music?.let { previewMusic ->
                PreviewLyricPanel(
                    preview = state.preview,
                    music = previewMusic,
                    lyrics = state.targetLyrics,
                    onPlayPause = { viewModel.sendIntent(SourceCorrectIntent.TogglePreviewPlayPause) },
                    onClose = { viewModel.sendIntent(SourceCorrectIntent.StopPreview) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("确认替换音源") },
            text = {
                Text(
                    when (state.activeTab) {
                        SourceCorrectTab.SEARCH ->
                            "将把「${state.targetMusic?.title ?: "当前歌曲"}」的音源替换为「${state.selectedThirdParty?.title ?: ""}」，替换后会自动重新分析高潮片段。"
                        SourceCorrectTab.UPLOAD ->
                            "将把「${state.targetMusic?.title ?: "当前歌曲"}」的音源替换为上传的文件「${state.pickedFileName ?: ""}」，替换后会自动重新分析高潮片段。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplaceConfirm = false
                        when (state.activeTab) {
                            SourceCorrectTab.SEARCH ->
                                viewModel.sendIntent(SourceCorrectIntent.ReplaceFromThirdParty)
                            SourceCorrectTab.UPLOAD ->
                                viewModel.sendIntent(SourceCorrectIntent.ReplaceFromFile)
                        }
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

@Composable
private fun SearchReplaceTab(
    query: String,
    platform: ManageSearchPlatform,
    hasSearched: Boolean,
    musicPaging: Flow<PagingData<Music>>,
    searchKey: Int,
    selectedMusic: Music?,
    /** 当前歌曲时长（毫秒）：用于「疑似同一首歌」提示，防无效替换 */
    targetDurationMs: Long?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectPlatform: (ManageSearchPlatform) -> Unit,
    onSelectMusic: (Music?) -> Unit,
    onPreview: (Music) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入关键词搜索...") },
                singleLine = true,
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Button(onClick = onSearch) {
                Text("搜索")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ManageSearchPlatform.entries.forEach { item ->
                FilterChip(
                    selected = platform == item,
                    onClick = { onSelectPlatform(item) },
                    label = { Text(item.label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!hasSearched) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "搜索网易云 / QQ 音乐，试听选中后替换",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            key(searchKey) {
                SourceCorrectResultList(
                    musicPaging = musicPaging,
                    selectedMusic = selectedMusic,
                    targetDurationMs = targetDurationMs,
                    onSelectMusic = onSelectMusic,
                    onPreview = onPreview,
                )
            }
        }
    }
}

@Composable
private fun SourceCorrectResultList(
    musicPaging: Flow<PagingData<Music>>,
    selectedMusic: Music?,
    targetDurationMs: Long?,
    onSelectMusic: (Music?) -> Unit,
    onPreview: (Music) -> Unit,
) {
    val musicItems = musicPaging.collectAsLazyPagingItems()

    when (val refresh = musicItems.loadState.refresh) {
        is LoadState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }

        is LoadState.Error -> {
            ErrorView(
                message = refresh.error.message ?: "搜索失败",
                onRetry = { musicItems.retry() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        else -> {
            if (musicItems.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "没有搜索结果",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                ) {
                    items(
                        count = musicItems.itemCount,
                        // 注意：第三方搜索结果中同一首歌可能重复出现，仅用 sourceId_id 会撞 key 崩溃，必须拼上 index
                        key = { index ->
                            musicItems.peek(index)?.let { "${index}_${it.sourceId}_${it.id}" }
                                ?: index
                        },
                    ) { index ->
                        val music = musicItems[index] ?: return@items
                        SourceCorrectRow(
                            music = music,
                            selected = selectedMusic != null &&
                                selectedMusic.sourceId == music.sourceId &&
                                selectedMusic.id == music.id,
                            targetDurationMs = targetDurationMs,
                            onSelect = { onSelectMusic(music) },
                            onPreview = { onPreview(music) },
                        )
                    }
                    item {
                        when (musicItems.loadState.append) {
                            is LoadState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }

                            is LoadState.Error -> {
                                TextButton(
                                    onClick = { musicItems.retry() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("加载失败，点击重试")
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCorrectRow(
    music: Music,
    selected: Boolean,
    targetDurationMs: Long?,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    // 候选与当前音源时长差 < 3 秒：极可能是同一首歌，替换后内容不会变，需醒目提示
    val sameAsCurrent = targetDurationMs != null &&
        targetDurationMs > 0 &&
        music.duration > 0 &&
        kotlin.math.abs(music.duration - targetDurationMs) < 3_000
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier.size(52.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artists.joinToString(", ") { it.name }.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sameAsCurrent) {
                Text(
                    text = "⚠ 疑似当前音源（时长几乎一致，替换后内容可能不变）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
        if (music.url.isNotBlank()) {
            TextButton(onClick = onPreview) {
                Text("试听")
            }
        }
    }
}

@Composable
private fun UploadReplaceTab(
    pickedFileName: String?,
    onFilePicked: (android.net.Uri?, String?) -> Unit,
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

@Composable
private fun PreviewLyricPanel(
    preview: PreviewPlaybackState,
    music: Music,
    lyrics: List<LyricsParser.LyricLine>,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (preview.durationMs > 0) {
        preview.positionMs.toFloat() / preview.durationMs.toFloat()
    } else {
        0f
    }
    val listState = rememberLazyListState()
    // 当前歌词行：跟随试听进度（歌词为原歌曲的，用于对照候选音源是否正确）
    val currentIndex = lyrics.indexOfLast { preview.positionMs >= it.startTimeMs }
    LaunchedEffect(currentIndex, lyrics.size) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "原曲歌词 · 对照试听",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (lyrics.isEmpty()) {
                    Text(
                        text = "暂无歌词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        itemsIndexed(
                            items = lyrics,
                            key = { index, _ -> index },
                        ) { index, line ->
                            val isCurrent = index == currentIndex
                            Text(
                                text = line.text,
                                style = if (isCurrent) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CachedImage(
                    imageUrl = music.coverImage,
                    contentDescription = music.title,
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = music.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = music.artists.joinToString(", ") { it.name },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (preview.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (preview.isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭试听")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FoxLinearProgress(progress = { progress.coerceIn(0f, 1f) })
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatMs(preview.positionMs)} / ${formatMs(preview.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
