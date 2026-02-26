package com.fox.music.feature.player.ui.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.Music
import com.fox.music.core.model.PlayerState
import com.fox.music.core.player.controller.MusicController

/**
 *    Author : 罗福林
 *    Date   : 2026/2/26
 *    Desc   : 播放列表底部工作表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    modifier: Modifier = Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    onDismiss: () -> Unit = {}
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    // 固定的深色用于文字，确保始终清晰可见
    val textColor = MaterialTheme.colorScheme.primary
    val secondaryTextColor = MaterialTheme.colorScheme.secondary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Color(0xFFF6F7F9),
        scrimColor = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // 标题栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "播放列表",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = "Close",
                        tint = textColor,
                        modifier = Modifier.size(24.dp).rotate(-90f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 播放列表计数
            Text(
                text = "共${playerState.playlist.size}首歌曲",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryTextColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 播放列表内容
            if (playerState.playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "播放列表为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(playerState.playlist) { index, music ->
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
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            if (playerState.playlist.isNotEmpty()) {
                Button(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                    Text("清空列表")
                }
            }
        }
    }

    // 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空播放列表") },
            text = { Text("确定要清空播放列表吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        musicController.clearQueue()
                        showClearConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearConfirm = false }
                ) {
                    Text("取消")
                }
            }
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
    onDeleteClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentPlaying)
                    Color(0xFF202122).copy(alpha = 0.08f)
                else
                    Color.Transparent
            )
            .clickable { onPlayClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 当前播放指示器
        if (isCurrentPlaying) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Now Playing",
                modifier = Modifier.size(20.dp),
                tint = textColor
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }

        // ...existing code...
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = music.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ...existing code...
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = secondaryTextColor
            )
        }
    }
}



