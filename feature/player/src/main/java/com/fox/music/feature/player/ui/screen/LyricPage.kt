package com.fox.music.feature.player.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.LyricsParser
import com.fox.music.core.model.PlayerState
import com.fox.music.feature.player.R
import kotlinx.coroutines.delay

/**
 *    Author : 罗福林
 *    Date   : 2026/2/4
 *    Desc   :
 */
@Composable
fun LyricPage(
    modifier: Modifier,
    playerState: PlayerState,
    displayComment: String? = null,
    dominantColor: Color,
    contrastColor: Color,
) {
    val lyricLines by remember {derivedStateOf {playerState.currentMusic?.lyricLines}}
    Column(modifier) {
        LyricComponent(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            lyricLines ?: emptyList(),
            playerState.position,
            {},
            playerState.isPlaying,
            inactiveLineColor = contrastColor
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(contrastColor, CircleShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_chat), contentDescription = null,
                    Modifier.padding(horizontal = 10.dp)
                )
                Text(
                    displayComment ?: "暂无评论",
                    color = dominantColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight(590),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .padding(10.dp)
                    .size(40.dp)
                    .background(contrastColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(if (playerState.isPlaying) R.drawable.iv_pause else R.drawable.iv_play),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun LyricComponent(
    modifier: Modifier = Modifier,
    lyrics: List<LyricsParser.LyricLine>,
    currentTimeMs: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean = true,
    lineHeight: Dp = 60.dp,
    activeLineColor: Color = Color(0xFF4CAF50),
    inactiveLineColor: Color = Color.Gray,
    activeLineFontSize: Int = 20,
    inactiveLineFontSize: Int = 16,
) {
    val listState = rememberLazyListState()
    var isDragging by remember {mutableStateOf(false)}
    var dragOffset by remember {mutableFloatStateOf(0f)}

    // 当前高亮行索引
    val currentHighlightIndex = remember(lyrics, currentTimeMs) {
        findCurrentLyricIndex(lyrics, currentTimeMs)
    }

    // 自动滚动到当前歌词（仅在播放状态且非拖动时）
    LaunchedEffect(currentHighlightIndex, isDragging, isPlaying) {
        if (isPlaying && ! isDragging && currentHighlightIndex >= 0) {
            // 延迟一下确保拖动结束
            delay(100)
            listState.animateScrollToItem(
                index = maxOf(0, currentHighlightIndex - 2), // 提前两行显示
                scrollOffset = 0
            )
        }
    }

    // 拖动结束时的回调
    LaunchedEffect(isDragging) {
        if (! isDragging && dragOffset != 0f) {
            // 计算拖动后的时间位置
            val draggedTimeMs = calculateDraggedTime(
                lyrics = lyrics,
                listState = listState,
                dragOffset = dragOffset,
                lineHeight = lineHeight
            )

            // 回调拖动后的时间
            onSeek(draggedTimeMs)

            // 重置拖动偏移
            dragOffset = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState {delta ->
                    dragOffset += delta
                    isDragging = true
                },
                onDragStarted = {
                    isDragging = true
                },
                onDragStopped = {
                    isDragging = false
                }
            )
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lyrics) {index, lyricLine ->
                val isActive = index == currentHighlightIndex

                LyricLineItem(
                    lyricLine = lyricLine,
                    isActive = isActive,
                    lineHeight = lineHeight,
                    activeColor = activeLineColor,
                    inactiveColor = inactiveLineColor,
                    activeFontSize = activeLineFontSize,
                    inactiveFontSize = inactiveLineFontSize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        // 拖动指示器（可选）
        if (isDragging) {
            DraggingIndicator()
        }
    }
}

@Composable
private fun LyricLineItem(
    lyricLine: LyricsParser.LyricLine,
    isActive: Boolean,
    lineHeight: Dp,
    activeColor: Color,
    inactiveColor: Color,
    activeFontSize: Int,
    inactiveFontSize: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(lineHeight)
    ) {
        Text(
            text = lyricLine.text,
            color = if (isActive) activeColor else inactiveColor,
            fontSize = if (isActive) activeFontSize.sp else inactiveFontSize.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DraggingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.Red.copy(alpha = 0.7f))
    )
}

// 查找当前时间对应的歌词索引
private fun findCurrentLyricIndex(lyrics: List<LyricsParser.LyricLine>, currentTimeMs: Long): Int {
    if (lyrics.isEmpty()) return - 1

    // 二分查找当前时间对应的歌词行
    var left = 0
    var right = lyrics.size - 1
    var result = - 1

    while (left <= right) {
        val mid = left + (right - left) / 2
        val lyric = lyrics[mid]

        if (currentTimeMs >= lyric.startTimeMs) {
            result = mid
            left = mid + 1
        } else {
            right = mid - 1
        }
    }

    return result
}

// 计算拖动后的时间
private fun calculateDraggedTime(
    lyrics: List<LyricsParser.LyricLine>,
    listState: LazyListState,
    dragOffset: Float,
    lineHeight: Dp,
): Long {
    if (lyrics.isEmpty()) return 0L

    // 计算拖动的行数
    val linesDragged = (dragOffset / lineHeight.value).toInt()

    // 获取当前可视区域的大致索引
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo

    if (visibleItems.isEmpty()) return 0L

    // 使用中间可见项作为参考点
    val middleIndex = visibleItems[visibleItems.size / 2].index

    // 计算目标索引
    val targetIndex = (middleIndex - linesDragged).coerceIn(0, lyrics.lastIndex)

    // 返回目标歌词行的开始时间
    return lyrics[targetIndex].startTimeMs
}