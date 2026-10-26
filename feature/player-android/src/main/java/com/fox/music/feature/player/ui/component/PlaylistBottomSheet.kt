package com.fox.music.feature.player.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.core.ui.component.JumpToPlayingFabForList
import com.fox.music.core.ui.component.LocalJumpHighlight
import com.fox.music.core.ui.component.NowPlayingBars
import com.fox.music.core.ui.component.jumpHighlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 播放列表底部工作表。
 * 侧滑返回使用向下收起动画，避免系统默认「中心缩小」效果。
 *
 * 2026-10 改版（视觉重做 · v6）：
 * - **当前播放项**：左侧律动音条（播放时跳动）+ 标题强调色 + 左侧发光竖条 + 强调色淡渐变底，
 *   从「一个箭头」升级为有明确焦点的「正在播放」视觉。
 * - **序号化**：非当前项显示两位灰序号（01/02…），扫读更有节奏。
 * - **播放模式栏**：列表循环 / 单曲循环 / 随机，点按即切，强调色图标实时反馈。
 * - **拖拽抬升**：长按时被拖项缩放 1.04 + 投影 + 卡片圆角化，松手落位（排序逻辑不变）。
 * - 顶部整合封面 + 标题 + 来源/计数，右上角改为关闭 X；清空入口下移到底部操作栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    modifier: Modifier = Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    onDismiss: () -> Unit = {},
    onSourceClick: (PlaybackPlaylistSource) -> Unit = {},
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    val displaySource = playerState.playlistSource?.takeIf { it.isDisplayable }

    // ── 拖动排序状态 ────────────────────────────────────────────
    // draggingIndex：当前被拖项的下标；draggingOffset：相对原位的纵向偏移（px）。
    // 每跨过一行步长（行高 56dp + 间距 8dp）就与相邻项交换并回补偏移，
    // 拖拽跟手且不会「飞出去」。
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val rowStridePx = with(LocalDensity.current) { 64.dp.toPx() }

    fun dismissSheet(after: (() -> Unit)? = null) {
        scope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
                after?.invoke()
            }
        }
    }

    // 统一走 FoxSheet：22dp 顶圆角（原来这里写死 28dp，是全仓最大的那一个）+ drag handle。
    // modifier / scrimColor / sheetState / properties 原样透传，保证侧滑返回与拖拽跟手不变。
    FoxSheet(
        onDismissRequest = { dismissSheet() },
        modifier = modifier
            .statusBarsPadding()
            .onSizeChanged { sheetHeightPx = it.height.toFloat() }
            .graphicsLayer {
                val h = sheetHeightPx.takeIf { it > 0f } ?: size.height
                // 仅下滑，明确不做缩放
                translationY = h * backProgress.value
                scaleX = 1f
                scaleY = 1f
            },
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        // 禁用组件默认预测性返回（会带缩放）
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        // 必须在 Dialog 内注册，才能接管侧滑返回
        PredictiveBackHandler(enabled = !showClearConfirm) { progress ->
            try {
                progress.collect { event ->
                    backProgress.snapTo(event.progress)
                }
                backProgress.animateTo(1f, animationSpec = tween(180))
                try {
                    sheetState.hide()
                } finally {
                    onDismiss()
                }
            } catch (e: CancellationException) {
                backProgress.animateTo(0f, animationSpec = tween(180))
                throw e
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // ── 顶部：封面 + 标题 + 来源/计数 + 关闭 ──────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverUrl = displaySource?.coverUrl?.takeIf { it.isNotBlank() }
                val sourceClickable = displaySource != null
                val coverModifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (sourceClickable) Modifier.clickable { dismissSheet { onSourceClick(displaySource) } } else Modifier)
                if (coverUrl != null) {
                    CachedImage(
                        imageUrl = coverUrl,
                        contentDescription = displaySource?.title,
                        modifier = coverModifier,
                        shape = RoundedCornerShape(14.dp),
                        placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                        placeholderColor = surfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (sourceClickable) Modifier.clickable { dismissSheet { onSourceClick(displaySource) } } else Modifier),
                ) {
                    Text(
                        text = "播放列表",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                    )
                    val subtitle = buildString {
                        displaySource?.title?.let { append("来自$it") }
                        if (displaySource != null) append("  ·  ")
                        append("共 ${playerState.playlist.size} 首")
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { dismissSheet() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // ── 播放模式栏（点按切换） ───────────────────────────
            if (playerState.playlist.isNotEmpty()) {
                val mode = playerState.repeatMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceVariant.copy(alpha = 0.45f))
                        .clickable { musicController.setRepeatMode(nextRepeatMode(mode)) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = repeatIcon(mode),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = repeatLabel(mode),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "点按切换",
                        fontSize = 11.sp,
                        color = onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (playerState.playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "播放列表为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceVariant,
                        )
                    }
                }
            } else {
                val lastIndex = playerState.playlist.lastIndex
                val queueListState = rememberLazyListState()
                val jumpScope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    itemsIndexed(
                        playerState.playlist,
                        key = { _, music -> music.id },
                    ) { index, music ->
                        val isDragging = index == draggingIndex
                        PlaylistItem(
                            music = music,
                            index = index,
                            isCurrentPlaying = index == playerState.currentIndex,
                            isPlaying = playerState.isPlaying,
                            isDragging = isDragging,
                            accent = accent,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant,
                            onPlayClick = {
                                musicController.seekToQueueItem(index)
                                musicController.play()
                            },
                            onDeleteClick = {
                                musicController.removeFromQueue(index)
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 2f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = draggingOffset
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                        shadowElevation = 12f
                                        shape = RoundedCornerShape(14.dp)
                                    }
                                }
                                .pointerInput(music.id, lastIndex) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = index
                                            draggingOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            draggingOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            draggingOffset = 0f
                                        },
                                    ) { _, dragAmount ->
                                        draggingOffset += dragAmount.y
                                        var from = draggingIndex
                                            ?: return@detectDragGesturesAfterLongPress
                                        // 向下跨过一行步长：与下一项交换
                                        while (draggingOffset > rowStridePx && from < lastIndex) {
                                            musicController.moveQueueItem(from, from + 1)
                                            from += 1
                                            draggingOffset -= rowStridePx
                                        }
                                        // 向上跨过一行步长：与上一项交换
                                        while (draggingOffset < -rowStridePx && from > 0) {
                                            musicController.moveQueueItem(from, from - 1)
                                            from -= 1
                                            draggingOffset += rowStridePx
                                        }
                                        draggingIndex = from
                                    }
                                },
                        )
                    }
                }
                val currentIndex = playerState.currentIndex
                val targetIndex =
                    if (playerState.playlist.isNotEmpty() && currentIndex in 0..lastIndex) {
                        currentIndex
                    } else {
                        -1
                    }
                val jumpHighlight = LocalJumpHighlight.current
                JumpToPlayingFabForList(
                    listState = queueListState,
                    targetIndex = targetIndex,
                    onClick = {
                        jumpScope.launch {
                            queueListState.animateScrollToItem(currentIndex)
                            // 到位后再闪一下，强调「就是这一行」
                            jumpHighlight.flash(playerState.currentMusic?.id)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
                }
            }

            // ── 底部操作栏：清空 ────────────────────────────────
            if (playerState.playlist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "清空播放列表",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showClearConfirm) {
        FoxDialog(
            title = "清空播放列表",
            text = "确定要清空播放列表吗？此操作不可恢复。",
            confirmText = "清空",
            destructive = true,
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                musicController.clearQueue()
                showClearConfirm = false
                dismissSheet()
            },
        )
    }
}

@Composable
private fun PlaylistItem(
    music: Music,
    index: Int,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    accent: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    onPlayClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        isCurrentPlaying -> accent.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onPlayClick() }
            .jumpHighlight(music.id),
    ) {
        // 当前播放：左侧发光竖条（拖拽时不显示，避免被缩放拉伸）
        if (isCurrentPlaying && !isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 3.dp)
                    .width(3.dp)
                    .height(26.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：当前播放音条 / 否则两位序号
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrentPlaying) {
                    NowPlayingBars(
                        color = accent,
                        bars = 4,
                        isPlaying = isPlaying,
                        modifier = Modifier.height(18.dp),
                    )
                } else {
                    Text(
                        text = String.format("%02d", index + 1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = music.title,
                    fontSize = 14.5.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrentPlaying) accent else onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = music.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown" },
                    fontSize = 11.5.sp,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = onSurfaceVariant,
                )
            }
            // 拖拽柄：明示「这项可以拖」（整行均可长按拖动）
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "拖动排序",
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = 2.dp),
                tint = onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

private fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.RANDOM
    RepeatMode.RANDOM -> RepeatMode.ALL
    RepeatMode.SEQUENTIAL -> RepeatMode.ONE
}

private fun repeatIcon(mode: RepeatMode) = when (mode) {
    RepeatMode.ALL -> Icons.Default.Repeat
    RepeatMode.ONE -> Icons.Default.RepeatOne
    RepeatMode.RANDOM -> Icons.Default.Shuffle
    RepeatMode.SEQUENTIAL -> Icons.Default.Repeat
}

private fun repeatLabel(mode: RepeatMode) = when (mode) {
    RepeatMode.ALL -> "列表循环"
    RepeatMode.ONE -> "单曲循环"
    RepeatMode.RANDOM -> "随机播放"
    RepeatMode.SEQUENTIAL -> "顺序播放"
}
