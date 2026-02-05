package com.fox.music.feature.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.ConvertUtils.px2dp
import com.fox.music.core.model.LyricsParser
import com.fox.music.core.model.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.player.R
import com.fox.music.feature.player.lyric.data.LyricStyle
import kotlinx.coroutines.delay

@Composable
fun LyricPage(
    modifier: Modifier,
    style: LyricStyle,
    displayComment: String? = null,
    musicController: MusicController,
    dominantColor: Color,
    contrastColor: Color,
) {
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val bilingualLyricLines by remember {
        derivedStateOf {
            playerState.currentMusic?.bilingualLyricLines ?: emptyList()
        }
    }
    val isPlaying by remember {derivedStateOf {playerState.isPlaying}}
    val position by remember {derivedStateOf {playerState.position}}

    Column(modifier.fillMaxSize()) {
        BilingualLyricComponent(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            lyrics = bilingualLyricLines,
            currentTimeMs = position,
            onSeek = {timeMs ->
                musicController.seekTo(timeMs)
            },
            activeLineColor = Color(style.highlightColor),
            inactiveLineColor = Color(style.textColor),
            activeLineFontSize = style.fontSize + 4,
            inactiveLineFontSize = style.fontSize
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 评论区域
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(contrastColor, CircleShape)
                    .clickable { /* 打开评论 */},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chat),
                    contentDescription = "评论",
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    tint = dominantColor
                )
                Text(
                    displayComment ?: "暂无评论",
                    color = dominantColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 播放/暂停按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(contrastColor, CircleShape)
                    .clickable {
                        musicController.togglePlay()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (playerState.isLoading){
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }else{
                    Icon(
                        painter = painterResource(
                            if (isPlaying) com.fox.music.core.common.R.drawable.iv_pause
                            else com.fox.music.core.common.R.drawable.iv_play
                        ),
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = dominantColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 双语歌词组件
 */
@Composable
fun BilingualLyricComponent(
    modifier: Modifier = Modifier,
    lyrics: List<LyricsParser.BilingualLyricLine>,
    currentTimeMs: Long,
    onSeek: (Long) -> Unit,
    activeLineColor: Color = Color(0xFF4CAF50),
    inactiveLineColor: Color = Color.Gray,
    activeLineFontSize: Int = 20,
    inactiveLineFontSize: Int = 16,
) {
    val listState = rememberLazyListState()

    // 当前高亮行索引
    val currentHighlightIndex = remember(lyrics, currentTimeMs) {
        findCurrentBilingualLyricIndex(lyrics, currentTimeMs)
    }

    val linesHeight = remember {mutableMapOf<Int, Float>()}

    var nextAutoScrollableTime by remember {mutableLongStateOf(System.currentTimeMillis())}

    LaunchedEffect(lyrics) {
        linesHeight.clear()
    }

    BoxWithConstraints(modifier) {
        val maxHeight = constraints.maxHeight
        val halfHeight = px2dp(maxHeight / 2f).toFloat()
        // 自动滚动到高亮行居中
        LaunchedEffect(currentHighlightIndex, nextAutoScrollableTime) {
            if (currentHighlightIndex != - 1 && ! listState.isScrollInProgress && System.currentTimeMillis() >= nextAutoScrollableTime) {
                listState.animateScrollToItem(
                    currentHighlightIndex + 1,
                    - (maxHeight / 2f - (linesHeight[currentHighlightIndex] ?: 0f)).toInt()
                )
            }
        }
        LaunchedEffect(listState.isScrollInProgress) {
            if (! listState.isScrollInProgress) {
                delay(2000)
                nextAutoScrollableTime = System.currentTimeMillis() - 100
            }
        }
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部留白
            item {
                Spacer(modifier = Modifier.height(halfHeight.dp))
            }

            itemsIndexed(lyrics) {index, lyricLine ->
                val isActive = index == currentHighlightIndex
                var padding by remember {mutableStateOf(8.dp)}
                BilingualLyricLineItem(
                    lyricLine = lyricLine,
                    isActive = isActive,
                    activeColor = activeLineColor,
                    inactiveColor = inactiveLineColor,
                    activeFontSize = activeLineFontSize,
                    inactiveFontSize = inactiveLineFontSize,
                    onClick = {
                        onSeek(lyricLine.startTimeMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            if (linesHeight[index] == null) {
                                val height = px2dp(it.size.height.toFloat())
                                padding = height.dp / 2f
                                linesHeight[index] = height.toFloat()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = padding)
                )
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.height(halfHeight.dp))
            }
        }
    }
}

/**
 * 双语歌词行组件
 */
@Composable
private fun BilingualLyricLineItem(
    lyricLine: LyricsParser.BilingualLyricLine,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeFontSize: Int,
    inactiveFontSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        // 原文歌词
        Text(
            text = lyricLine.originalText,
            color = if (isActive) activeColor else inactiveColor,
            fontSize = if (isActive) activeFontSize.sp else inactiveFontSize.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // 翻译歌词（如果存在）
        if (! lyricLine.translatedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lyricLine.translatedText ?: "",
                color = if (isActive) activeColor.copy(alpha = 0.9f) else inactiveColor.copy(alpha = 0.7f),
                fontSize = if (isActive) (activeFontSize - 2).sp else (inactiveFontSize - 2).sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = activeFontSize.sp
            )
        }
    }
}

/**
 * 查找当前时间对应的双语歌词索引
 */
private fun findCurrentBilingualLyricIndex(
    lyrics: List<LyricsParser.BilingualLyricLine>,
    currentTimeMs: Long,
): Int {
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