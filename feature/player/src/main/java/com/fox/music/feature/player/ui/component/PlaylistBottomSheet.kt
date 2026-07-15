package com.fox.music.feature.player.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 播放列表底部工作表。
 * 侧滑返回使用向下收起动画，避免系统默认「中心缩小」效果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    modifier: Modifier = Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    onDismiss: () -> Unit = {},
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val textColor = MaterialTheme.colorScheme.primary
    val secondaryTextColor = MaterialTheme.colorScheme.secondary
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    fun dismissSheet() {
        scope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
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
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetState = sheetState,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "播放列表",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                if (playerState.playlist.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear",
                            tint = textColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "共${playerState.playlist.size}首歌曲",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryTextColor,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (playerState.playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "播放列表为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(
                        playerState.playlist,
                        key = { _, music -> music.id },
                    ) { index, music ->
                        PlaylistItem(
                            music = music,
                            isCurrentPlaying = index == playerState.currentIndex,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            onPlayClick = {
                                musicController.seekToQueueItem(index)
                                musicController.play()
                            },
                            onDeleteClick = {
                                musicController.removeFromQueue(index)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空播放列表") },
            text = { Text("确定要清空播放列表吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        musicController.clearQueue()
                        dismissSheet()
                    },
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                Button(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun PlaylistItem(
    music: Music,
    isCurrentPlaying: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onPlayClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentPlaying) {
                    Color(0xFF202122).copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .clickable { onPlayClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (isCurrentPlaying) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Now Playing",
                modifier = Modifier.size(20.dp),
                tint = textColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
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
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = secondaryTextColor,
            )
        }
    }
}
