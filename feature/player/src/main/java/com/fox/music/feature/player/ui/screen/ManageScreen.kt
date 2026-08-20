package com.fox.music.feature.player.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.SearchBar
import com.fox.music.feature.player.viewmodel.ManageEffect
import com.fox.music.feature.player.viewmodel.ManageIntent
import com.fox.music.feature.player.viewmodel.ManageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

const val MANAGE_ROUTER = "manage"
const val MANAGE_SEARCH_ROUTE = "manage_search"

private const val ManageMenuAnimMs = 220
private val ManageMenuFloatSpec = tween<Float>(
    durationMillis = ManageMenuAnimMs,
    easing = FastOutSlowInEasing,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onAddMusic: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val addIconRotation by animateFloatAsState(
        targetValue = if (state.showActionsMenu) 45f else 0f,
        animationSpec = ManageMenuFloatSpec,
        label = "manageAddIconRotation",
    )

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ManageEffect.NavigateToSearch -> onAddMusic()
                ManageEffect.NavigateToLogin -> onLogin()
                is ManageEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("曲库管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.sendIntent(ManageIntent.Refresh) },
                        enabled = state.isLoggedIn && !state.isBusy,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(
                            onClick = { viewModel.sendIntent(ManageIntent.ToggleActionsMenu) },
                            enabled = state.isLoggedIn && !state.isBusy,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (state.showActionsMenu) {
                                    "关闭菜单"
                                } else {
                                    "更多操作"
                                },
                                modifier = Modifier.rotate(addIconRotation),
                            )
                        }
                        ManageActionsPopup(
                            expanded = state.showActionsMenu,
                            onDismiss = {
                                viewModel.sendIntent(ManageIntent.DismissActionsMenu)
                            },
                            onAddMusic = {
                                viewModel.sendIntent(ManageIntent.RequestAddMusic)
                            },
                            onUpdateCharts = {
                                viewModel.sendIntent(ManageIntent.RequestUpdateCharts)
                            },
                            onUpdateHotPlaylists = {
                                viewModel.sendIntent(ManageIntent.RequestUpdateHotPlaylists)
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "需要登录后才能管理曲库",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.sendIntent(ManageIntent.RequestLogin) }) {
                        Text("登录")
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchBar(
                query = state.query,
                onQueryChange = { viewModel.sendIntent(ManageIntent.QueryChange(it)) },
                onSearch = { viewModel.sendIntent(ManageIntent.Search) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "搜索曲库...",
            )

            if (state.isBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            state.isUpdatingCharts -> "正在更新排行榜..."
                            state.isUpdatingHotPlaylists -> "正在启动热门歌单同步..."
                            else -> "处理中..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            key(state.listRefreshKey) {
                ManageMusicList(
                    musicPaging = state.musicPaging,
                    onDelete = { music ->
                        viewModel.sendIntent(ManageIntent.RequestDelete(music))
                    },
                )
            }
        }
    }

    state.pendingDelete?.let { music ->
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(ManageIntent.DismissDelete) },
            title = { Text("删除音乐") },
            text = { Text("确定删除「${music.title}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.sendIntent(ManageIntent.ConfirmDelete) }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.sendIntent(ManageIntent.DismissDelete) }) {
                    Text("取消")
                }
            },
        )
    }

    if (state.showChartsConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(ManageIntent.DismissChartsConfirm) },
            title = { Text("更新排行榜") },
            text = {
                Text("将导入网易云官方榜单（每榜最多约 50 首），可能需要一段时间，确定继续？")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.sendIntent(ManageIntent.ConfirmUpdateCharts) }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.sendIntent(ManageIntent.DismissChartsConfirm) }) {
                    Text("取消")
                }
            },
        )
    }

    if (state.showHotPlaylistsConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(ManageIntent.DismissHotPlaylistsConfirm) },
            title = { Text("更新热门歌单") },
            text = {
                Text("将在后台同步热门歌单，导入的歌单会关联到当前登录账号，确定继续？")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.sendIntent(ManageIntent.ConfirmUpdateHotPlaylists) },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.sendIntent(ManageIntent.DismissHotPlaylistsConfirm) },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ManageActionsPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddMusic: () -> Unit,
    onUpdateCharts: () -> Unit,
    onUpdateHotPlaylists: () -> Unit,
) {
    var renderPopup by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            renderPopup = true
            menuVisible = true
        } else {
            menuVisible = false
            delay(ManageMenuAnimMs.toLong())
            renderPopup = false
        }
    }

    if (!renderPopup) return

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn(ManageMenuFloatSpec) +
                expandVertically(
                    animationSpec = tween(ManageMenuAnimMs, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) +
                scaleIn(
                    animationSpec = ManageMenuFloatSpec,
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
            exit = fadeOut(ManageMenuFloatSpec) +
                shrinkVertically(
                    animationSpec = tween(ManageMenuAnimMs, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) +
                scaleOut(
                    animationSpec = ManageMenuFloatSpec,
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(top = 48.dp, end = 8.dp),
            ) {
                Column(modifier = Modifier.width(180.dp)) {
                    DropdownMenuItem(
                        text = { Text("添加单曲") },
                        onClick = onAddMusic,
                    )
                    DropdownMenuItem(
                        text = { Text("更新排行榜") },
                        onClick = onUpdateCharts,
                    )
                    DropdownMenuItem(
                        text = { Text("更新热门歌单") },
                        onClick = onUpdateHotPlaylists,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageMusicList(
    musicPaging: Flow<PagingData<Music>>,
    onDelete: (Music) -> Unit,
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
                message = refresh.error.message ?: "加载失败",
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
                        text = "暂无音乐",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        count = musicItems.itemCount,
                        key = { index -> musicItems.peek(index)?.id ?: index },
                    ) { index ->
                        val music = musicItems[index] ?: return@items
                        ManageMusicRow(
                            music = music,
                            onDelete = { onDelete(music) },
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
private fun ManageMusicRow(
    music: Music,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(12.dp))
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
            music.album?.title?.takeIf { it.isNotBlank() }?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onDelete) {
            Text("删除", color = MaterialTheme.colorScheme.error)
        }
    }
}
