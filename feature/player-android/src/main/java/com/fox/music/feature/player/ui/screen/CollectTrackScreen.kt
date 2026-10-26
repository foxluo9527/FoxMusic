package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.ui.component.FoxDualPrimaryDialog
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PlaylistPickerBottomSheet
import com.fox.music.core.ui.component.PreviewLyricPanel
import com.fox.music.feature.player.viewmodel.CollectTrackArgs
import com.fox.music.feature.player.viewmodel.CollectTrackEffect
import com.fox.music.feature.player.viewmodel.CollectTrackIntent
import com.fox.music.feature.player.viewmodel.CollectTrackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectTrackScreen(
    args: CollectTrackArgs?,
    modifier: Modifier = Modifier,
    viewModel: CollectTrackViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    /**
     * 收录成功后的额外动作。默认空实现＝留在本页（按钮变「已收录」）。
     *
     * 从搜索页「去收录」进来的流程（args.fromSearch=true）由导航层传入「返回搜索页」，
     * 让用户提交完直接回到搜索结果看到新收录的歌，而不是停在音源搜索页。
     */
    onCollectSucceeded: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showQueryEditor by remember { mutableStateOf(false) }

    LaunchedEffect(args) {
        args?.let { viewModel.initWithArgs(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CollectTrackEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                CollectTrackEffect.CollectSucceeded -> onCollectSucceeded()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            "add_single" -> "添加单曲"
                            else -> "收录到平台"
                        },
                    )
                },
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (state.exists?.exists == true) {
                        Text(
                            text = "该歌曲已收录到平台，提交将复用已有条目",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = when {
                            state.resolvedCandidate != null ->
                                "已选并解析：${state.resolvedCandidate?.title}"
                            state.selectedCandidateId != null ->
                                "已选：${state.selectedCandidate?.title}（解析中…）"
                            else -> "请从上方选择一个音源候选（点击自动解析）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.sendIntent(CollectTrackIntent.ShowCollectChoice) },
                        enabled = state.resolvedCandidate != null && !state.isCollecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isCollecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("收录中…")
                        } else {
                            Text("提交收录")
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
                SearchQueryBar(
                    query = state.editTitle,
                    artistQuery = state.editArtist,
                    isSearching = state.isSearching,
                    onEditQuery = { showQueryEditor = true },
                    onSearch = { viewModel.sendIntent(CollectTrackIntent.SearchProviders) },
                )

                if (state.groups.isNotEmpty()) {
                    // tab 可超出屏幕左右滑动；数量不放 tab，改在列表 footer 展示（用户 2026-10-21）
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
                                onClick = {
                                    viewModel.sendIntent(CollectTrackIntent.SelectGroup(group.key))
                                },
                                text = { Text(group.label, maxLines = 1) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val candidates = state.activeCandidates
                when {
                    state.isSearching -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    state.groups.isEmpty() -> {
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
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(
                                items = candidates,
                                key = { index, candidate -> "${index}_${candidate.id}" },
                            ) { _, candidate ->
                                val resolved = state.resolvedUrls.containsKey(candidate.id) ||
                                    candidate.playType == CandidatePlayType.DIRECT
                                val isPreviewing = state.previewCandidateId == candidate.id
                                val isBuffering = isPreviewing && state.preview.isBuffering
                                CandidateRow(
                                    candidate = candidate,
                                    selected = state.selectedCandidateId == candidate.id,
                                    resolved = resolved,
                                    isResolving = state.resolvingIds.contains(candidate.id),
                                    isPreviewing = isPreviewing && state.preview.isPlaying,
                                    isBuffering = isBuffering,
                                    targetDurationMs = state.argsDurationMs.takeIf { it > 0 },
                                    onSelect = {
                                        viewModel.sendIntent(CollectTrackIntent.SelectCandidate(candidate))
                                    },
                                    onTogglePlay = {
                                        viewModel.sendIntent(CollectTrackIntent.ToggleCandidatePlay(candidate))
                                    },
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

            state.preview.music?.let { previewMusic ->
                PreviewLyricPanel(
                    title = previewMusic.title,
                    artist = previewMusic.artists.joinToString(", ") { it.name },
                    positionMs = state.preview.positionMs,
                    durationMs = state.preview.durationMs,
                    isPlaying = state.preview.isPlaying,
                    isBuffering = state.preview.isBuffering,
                    lyrics = state.previewLyrics,
                    onPlayPause = {
                        viewModel.sendIntent(CollectTrackIntent.TogglePreviewPlayPause)
                    },
                    onSeek = { viewModel.sendIntent(CollectTrackIntent.SeekPreview(it)) },
                    onClose = { viewModel.sendIntent(CollectTrackIntent.StopPreview) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showQueryEditor) {
        QueryEditorDialog(
            initialTitle = state.editTitle,
            initialArtist = state.editArtist,
            onDismiss = { showQueryEditor = false },
            onConfirm = { title, artist ->
                showQueryEditor = false
                viewModel.sendIntent(CollectTrackIntent.EditTitleChange(title))
                viewModel.sendIntent(CollectTrackIntent.EditArtistChange(artist))
                viewModel.sendIntent(CollectTrackIntent.SearchProviders)
            },
        )
    }

    // 收录方式选择：平台 / 歌单
    if (state.showCollectChoice) {
        FoxDualPrimaryDialog(
            title = "收录到",
            text = "选择收录到平台曲库，或加入你自己的歌单。",
            firstText = "平台",
            onFirst = { viewModel.sendIntent(CollectTrackIntent.CollectToPlatform) },
            secondText = "歌单",
            onSecond = { viewModel.sendIntent(CollectTrackIntent.CollectToPlaylist) },
            onDismiss = { viewModel.sendIntent(CollectTrackIntent.DismissCollectChoice) },
        )
    }

    // 歌单选择
    if (state.showPlaylistPicker) {
        PlaylistPickerBottomSheet(
            playlists = state.playlists,
            isLoading = state.isPlaylistsLoading,
            onDismiss = { viewModel.sendIntent(CollectTrackIntent.DismissPlaylistPicker) },
            onPlaylistSelected = {
                viewModel.sendIntent(CollectTrackIntent.PickPlaylist(it))
            },
            onCreatePlaylist = { /* 暂不支持在此新建，可后续扩展 */ },
        )
    }
}
