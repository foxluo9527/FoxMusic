package com.fox.music.feature.player.ui.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.InputDialog
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.SearchBar
import com.fox.music.feature.player.viewmodel.ManageSearchEffect
import com.fox.music.feature.player.viewmodel.ManageSearchIntent
import com.fox.music.feature.player.viewmodel.ManageSearchPlatform
import com.fox.music.feature.player.viewmodel.ManageSearchViewModel
import com.fox.music.feature.player.viewmodel.PreviewPlaybackState
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ManageSearchEffect.NavigateToLogin -> onLogin()
                is ManageSearchEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("搜索音乐") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.sendIntent(ManageSearchIntent.ShowPlaylistImport) }) {
                        Text("导入歌单")
                    }
                },
            )
        },
        bottomBar = {
            if (state.selectedCount > 0) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已选 ${state.selectedCount} 首",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { viewModel.sendIntent(ManageSearchIntent.RequestImportSelected) },
                            enabled = state.importProgress == null,
                        ) {
                            Text("导入")
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
                SearchBar(
                    query = state.query,
                    onQueryChange = { viewModel.sendIntent(ManageSearchIntent.QueryChange(it)) },
                    onSearch = { viewModel.sendIntent(ManageSearchIntent.Search) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = "输入关键词搜索...",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ManageSearchPlatform.entries.forEach { platform ->
                        FilterChip(
                            selected = state.platform == platform,
                            onClick = {
                                viewModel.sendIntent(ManageSearchIntent.SelectPlatform(platform))
                            },
                            label = { Text(platform.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!state.hasSearched) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "搜索第三方平台并导入到曲库",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    key(state.searchKey) {
                        ManageSearchResultList(
                            musicPaging = state.musicPaging,
                            selectedIds = state.selectedIds,
                            selectAllChecked = state.selectAllChecked,
                            onToggleSelect = {
                                viewModel.sendIntent(ManageSearchIntent.ToggleSelect(it))
                            },
                            onSelectAll = { checked, visible ->
                                viewModel.sendIntent(ManageSearchIntent.SetSelectAll(checked, visible))
                            },
                            onPreview = {
                                viewModel.sendIntent(ManageSearchIntent.Preview(it))
                            },
                        )
                    }
                }
            }

            state.preview.music?.let { previewMusic ->
                PreviewMiniBar(
                    preview = state.preview,
                    music = previewMusic,
                    onPlayPause = { viewModel.sendIntent(ManageSearchIntent.TogglePreviewPlayPause) },
                    onClose = { viewModel.sendIntent(ManageSearchIntent.StopPreview) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (state.showPlaylistImport) {
        InputDialog(
            title = "导入歌单",
            label = "歌单分享链接",
            placeholder = "请输入网易云或 QQ 音乐歌单链接",
            confirmText = "导入",
            onDismiss = { viewModel.sendIntent(ManageSearchIntent.DismissPlaylistImport) },
            onConfirm = { viewModel.sendIntent(ManageSearchIntent.ConfirmPlaylistImport(it)) },
        )
    }

    state.importProgress?.let { progress ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                    if (progress.total > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                progress.current.toFloat() / progress.total.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${progress.current}/${progress.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageSearchResultList(
    musicPaging: Flow<PagingData<Music>>,
    selectedIds: Set<Long>,
    selectAllChecked: Boolean,
    onToggleSelect: (Music) -> Unit,
    onSelectAll: (Boolean, List<Music>) -> Unit,
    onPreview: (Music) -> Unit,
) {
    val musicItems = musicPaging.collectAsLazyPagingItems()
    val visibleItems = remember(musicItems.itemCount, musicItems.loadState) {
        buildList {
            for (i in 0 until musicItems.itemCount) {
                musicItems.peek(i)?.let { add(it) }
            }
        }
    }

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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selectAllChecked && visibleItems.isNotEmpty(),
                        onCheckedChange = { onSelectAll(it, visibleItems) },
                    )
                    Text("全选", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "已加载 ${musicItems.itemCount} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (musicItems.itemCount == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "没有搜索结果",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 96.dp,
                        ),
                    ) {
                        items(
                            count = musicItems.itemCount,
                            key = { index ->
                                musicItems.peek(index)?.let { "${it.sourceId}_${it.id}" } ?: index
                            },
                        ) { index ->
                            val music = musicItems[index] ?: return@items
                            ManageSearchRow(
                                music = music,
                                selected = music.id in selectedIds,
                                onToggle = { onToggleSelect(music) },
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
}

@Composable
private fun ManageSearchRow(
    music: Music,
    selected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
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
        }
        if (music.url.isNotBlank()) {
            TextButton(onClick = onPreview) {
                Text("试听")
            }
        }
    }
}

@Composable
private fun PreviewMiniBar(
    preview: PreviewPlaybackState,
    music: Music,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (preview.durationMs > 0) {
        preview.positionMs.toFloat() / preview.durationMs.toFloat()
    } else {
        0f
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
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
