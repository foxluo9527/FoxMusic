package com.fox.music.feature.playlist.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.DetailType
import com.fox.music.core.model.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.feature.playlist.viewmodel.HeaderInfo
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailEffect
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailState
import com.fox.music.feature.playlist.viewmodel.PlaylistDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String) -> Unit = {_, _, _ ->},
    updateMusicList: (List<Music>, String) -> Unit = {_, _ ->},
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.tracks.collectAsLazyPagingItems()
    val context = LocalContext.current
    var updateKey by remember {mutableStateOf("")}
    // 收集当前加载的歌曲列表
    LaunchedEffect(pagingItems.itemCount) {
        val currentList = (0 until pagingItems.itemCount).mapNotNull {pagingItems[it]}
        viewModel.updateCurrentTrackList(currentList)
    }
    LaunchedEffect(pagingItems.itemSnapshotList.items) {
        updateMusicList(pagingItems.itemSnapshotList.items, updateKey)
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collect {effect ->
            when(effect) {
                is PlaylistDetailEffect.NavigateToMusic -> {
                    val key = "collection_detail/${state.headerInfo?.id}"
                    onMusicClick(effect.music, effect.musicList, key)
                    updateKey = key
                }

                is PlaylistDetailEffect.PlayAllTracks -> {
                    val key = "collection_detail/${state.headerInfo?.id}"
                    if (effect.musicList.isNotEmpty()) {
                        onMusicClick(effect.musicList.first(), effect.musicList, key)
                        updateKey = key
                    }
                }

                is PlaylistDetailEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = getTypeTitle(state.detailType),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (state.detailType !== DetailType.RECOMMEND && state.detailType != DetailType.NEW_MUSIC) {
                        IconButton(onClick = { /* TODO: Search */}) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) {innerPadding ->
        when {
            state.isLoading && state.headerInfo == null -> {
                LoadingIndicator(useLottie = false)
            }

            state.error != null && state.headerInfo == null -> {
                ErrorView(message = state.error !!)
            }

            else -> {
                CollectionDetailContent(
                    state = state,
                    pagingItems = pagingItems,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMusicClick = {music ->
                        val currentList =
                            (0 until pagingItems.itemCount).mapNotNull {pagingItems[it]}
                        viewModel.onMusicClick(music, currentList)
                    },
                    onPlayAll = {viewModel.sendIntent(PlaylistDetailIntent.PlayAll)},
                    onToggleFavorite = {viewModel.sendIntent(PlaylistDetailIntent.ToggleFavorite)},
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun CollectionDetailContent(
    state: PlaylistDetailState,
    pagingItems: LazyPagingItems<Music>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 头部信息区域
        item {
            state.headerInfo?.let {info ->
                CollectionDetailHeader(
                    headerInfo = info,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        item {
            state.headerInfo?.let {headerInfo->
                ActionButtonRow(
                    showFavorite = state.detailType !== DetailType.RECOMMEND && state.detailType != DetailType.NEW_MUSIC,
                    trackCount = headerInfo.trackCount.takeIf {it > 0} ?: pagingItems.itemCount,
                    isFavorite = headerInfo.isFavorite,
                    onPlayAll = onPlayAll,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 歌曲列表
        items(
            count = pagingItems.itemCount,
            key = {index -> pagingItems[index]?.id ?: index}
        ) {index ->
            pagingItems[index]?.let {music ->
                MusicListItem(
                    music = music,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onClick = {onMusicClick(music)}
                )
            }
        }

        // 加载状态处理
        when {
            pagingItems.loadState.refresh is LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            pagingItems.loadState.append is LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            pagingItems.loadState.refresh is LoadState.Error -> {
                item {
                    val error = (pagingItems.loadState.refresh as LoadState.Error).error
                    ErrorView(
                        message = error.localizedMessage ?: "加载失败",
                        onRetry = {pagingItems.retry()}
                    )
                }
            }

            pagingItems.loadState.append is LoadState.Error -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载更多失败，点击重试",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable {pagingItems.retry()}
                        )
                    }
                }
            }

            pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 头部信息区域组件
 */
@Composable
private fun CollectionDetailHeader(
    headerInfo: HeaderInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (headerInfo.coverImage != null) {// 封面图
            CachedImage(
                imageUrl = headerInfo.coverImage,
                contentDescription = headerInfo.title,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 类型标签 + 创建时间
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = getTypeLabel(headerInfo.detailType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                headerInfo.createdAt?.let {date ->
                    Text(
                        text = " · ${formatDate(date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                headerInfo.releaseDate?.let {date ->
                    Text(
                        text = " · $date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 标题
            Text(
                text = headerInfo.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 曲目数 / 创建者
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                headerInfo.trackCount.takeIf {it > 0}?.let {
                    Text(
                        text = "${headerInfo.trackCount}首",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                headerInfo.creatorName?.let {creator ->
                    Text(
                        text = " · 创建者: $creator",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                headerInfo.artists?.let {artists ->
                    Text(
                        text = " · $artists",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 描述
            headerInfo.description?.takeIf {it.isNotBlank()}?.let {desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 操作按钮行组件
 */
@Composable
private fun ActionButtonRow(
    isFavorite: Boolean,
    showFavorite: Boolean = true,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    trackCount: Int?,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        ActionButton(
            icon = Icons.Default.PlayArrow,
            label = "播放全部($trackCount)",
            onClick = onPlayAll
        )
        if (showFavorite) {
            Spacer(Modifier.width(10.dp))
            ActionButton(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isFavorite) "已收藏" else "收藏",
                onClick = onToggleFavorite
            )
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(40.dp)
            .background(Color.White, CircleShape)
            .weight(1f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getTypeTitle(type: DetailType): String {
    return when(type) {
        DetailType.PLAYLIST -> "歌单详情"
        DetailType.ALBUM -> "专辑详情"
        DetailType.RANK -> "排行榜"
        DetailType.RECOMMEND -> "推荐歌曲"
        DetailType.NEW_MUSIC -> "新歌速递"
    }
}

private fun getTypeLabel(type: DetailType): String {
    return when(type) {
        DetailType.PLAYLIST -> "歌单"
        DetailType.ALBUM -> "专辑"
        DetailType.RANK -> "榜单"
        DetailType.RECOMMEND -> "推荐"
        DetailType.NEW_MUSIC -> "新歌"
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        dateStr.take(10) // 取 "YYYY-MM-DD" 部分
    } catch (e: Exception) {
        dateStr
    }
}
