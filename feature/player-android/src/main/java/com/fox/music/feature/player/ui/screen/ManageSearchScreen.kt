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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fox.music.core.ui.component.FoxLinearProgress
import com.fox.music.core.ui.component.InputDialog
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.SearchBar
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarTextAction
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
    onNavigateToCollect: (Music) -> Unit = {},
    initialQuery: String = "",
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 从「去收录」带入关键词：预填并自动搜索一次
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.sendIntent(ManageSearchIntent.QueryChange(initialQuery))
            viewModel.sendIntent(ManageSearchIntent.Search)
        }
    }

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
            FoxTopBar(
                title = "搜索音乐",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    FoxTopBarTextAction(
                        text = "导入歌单",
                        onClick = { viewModel.sendIntent(ManageSearchIntent.ShowPlaylistImport) },
                    )
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
                            onOpen = { music -> onNavigateToCollect(music) },
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
                        FoxLinearProgress(
                            progress = {
                                progress.current.toFloat() / progress.total.toFloat()
                            },
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
    onOpen: (Music) -> Unit,
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
                        // 注意：第三方搜索结果中同一首歌可能重复出现，仅用 sourceId_id 会撞 key 崩溃，必须拼上 index
                        key = { index ->
                            musicItems.peek(index)?.let { "${index}_${it.sourceId}_${it.id}" }
                                ?: index
                        },
                    ) { index ->
                        val music = musicItems[index] ?: return@items
                        ManageSearchRow(
                            music = music,
                            onOpen = { onOpen(music) },
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
private fun ManageSearchRow(
    music: Music,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        IconButton(onClick = onOpen) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
