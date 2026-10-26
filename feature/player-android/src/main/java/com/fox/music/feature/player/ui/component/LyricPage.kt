package com.fox.music.feature.player.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.ConvertUtils.px2dp
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.social.Comment
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.player.R
import com.fox.music.feature.player.lyric.data.LyricStyle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LyricPage(
    modifier: Modifier,
    style: LyricStyle,
    musicController: MusicController,
    dominantColor: Color,
    contrastColor: Color,
    comments: List<Comment> = emptyList(),
    onCommentClick: (Long) -> Unit = {}
) {
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val currentMusic = playerState.currentMusic
    // 显式依赖 lyrics，确保冷启动切歌后预取补全时会刷新
    val bilingualLyricLines = remember(
        currentMusic?.id,
        currentMusic?.lyrics,
        currentMusic?.lyricsTrans,
    ) {
        currentMusic?.bilingualLyricLines ?: emptyList()
    }
    val isPlaying = playerState.isPlaying
    val position = playerState.position

    // 冒泡队列：index 0 是最新冒出来的一条，直接显示在评论条上；越往后越旧。
    // 评论条本身就是第一个泡，再往上叠 3 个气泡，同屏最多 4 条评论。
    val arrivals = remember { mutableStateListOf<BubbleEntry>() }
    LaunchedEffect(comments) {
        arrivals.clear()
        if (comments.isEmpty()) return@LaunchedEffect
        delay(BUBBLE_FIRST_DELAY_MS)
        var cursor = 0
        var token = 0L
        while (true) {
            arrivals.add(0, BubbleEntry(token++, comments[cursor % comments.size]))
            while (arrivals.size > BUBBLE_SLOT_COUNT) arrivals.removeAt(arrivals.lastIndex)
            cursor++
            delay(BUBBLE_INTERVAL_MS)
        }
    }
    val barEntry = arrivals.firstOrNull()
    // 上方气泡从下往上排：挨着评论条的最新，越往上越旧。
    val floatingEntries = arrivals.drop(1).asReversed()

    Column(modifier.fillMaxSize()) {
        // 歌词与「评论冒泡」叠在同一层：气泡从评论条上沿升起，不参与排版，
        // 否则每冒一条气泡整段歌词都会往上跳一格。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            BilingualLyricComponent(
                modifier = Modifier.fillMaxSize(),
                lyrics = bilingualLyricLines,
                currentTimeMs = position,
                onSeek = { timeMs ->
                    musicController.seekTo(timeMs)
                },
                activeLineColor = Color(style.highlightColor),
                inactiveLineColor = Color(style.textColor),
                activeLineFontSize = style.fontSize + 4,
                inactiveLineFontSize = style.fontSize,
                showTranslation = style.showTranslation
            )

            CommentBubbleFeed(
                entries = floatingEntries,
                authorColor = contrastColor.copy(alpha = 0.5f),
                contentColor = contrastColor.copy(alpha = 0.86f),
                onClick = { onCommentClick(playerState.currentMusic?.id ?: 0L) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.9f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 横向不再补内距：外层 HorizontalPager 已经留了 24dp，
                // 这里再加 16dp 就变成 40dp，评论条被挤成一条窄缝。
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 评论区域
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape)
                    .border(1.dp, contrastColor.copy(alpha = 0.08f), CircleShape)
                    .clickable { onCommentClick(playerState.currentMusic?.id ?: 0L) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chat),
                    contentDescription = "评论",
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    // 之前用 dominantColor（接近页面底色）描图标和文字，
                    // 在白药丸上几乎看不见，读起来像「这块坏了」。
                    tint = contrastColor.copy(alpha = 0.55f)
                )
                val barComment = barEntry?.comment
                if (barComment == null) {
                    Text(
                        text = "暂无评论",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = contrastColor.copy(alpha = 0.42f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 评论条就是冒泡的第一个泡：有评论时直接显示最新一条，
                    // 上一条会被顶到上方气泡里，形成从评论条向上冒的效果。
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val author = barComment.authorName?.trim().orEmpty()
                        if (author.isNotEmpty()) {
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = contrastColor.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 96.dp),
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                        }
                        Text(
                            text = barComment.content.trim().replace('\n', ' '),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = contrastColor.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
 * 评论冒泡：从评论条上沿一条条浮起的评论气泡。
 *
 * 交互取自短视频/音乐 App 的「评论气泡」（抖音评论区浮层、汽水音乐播放页）：
 * 气泡不抢焦点、不打断当前操作，扫一眼就知道这首歌在聊什么，想看全文点气泡进评论页。
 * - 评论条本身就是第一个泡（显示最新一条），上方最多再叠 [BUBBLE_MAX_FLOATING] 个；
 * - 每 [BUBBLE_INTERVAL_MS] 冒一条：新的从评论条上沿出现，旧的被顶上去并淡出；
 * - 首条延迟 [BUBBLE_FIRST_DELAY_MS] 出现，避免刚进页面时和歌词抢视觉；
 * - 无评论时气泡区不占位，评论条回落「暂无评论」。
 *
 * 实现上用固定高度 + 贴底的 LazyColumn（旧的在上、新的在下）+ `animateItem()`：
 * 新条插入时旧的自动获得位移动画向上滑，最上面那条走淡出，不需要手写偏移。
 */
private const val BUBBLE_MAX_FLOATING = 3

/** 同屏最多 4 条评论 = 评论条本身 + 上方 3 个气泡。 */
private const val BUBBLE_SLOT_COUNT = BUBBLE_MAX_FLOATING + 1
private const val BUBBLE_INTERVAL_MS = 2900L
private const val BUBBLE_FIRST_DELAY_MS = 700L
private val BubbleRowHeight = 32.dp
private val BubbleRowGap = 7.dp

private data class BubbleEntry(val token: Long, val comment: Comment)

@Composable
private fun CommentBubbleFeed(
    entries: List<BubbleEntry>,
    authorColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    LazyColumn(
        modifier = modifier
            .height(BubbleRowHeight * BUBBLE_MAX_FLOATING + BubbleRowGap * (BUBBLE_MAX_FLOATING - 1))
            .clipToBounds(),
        state = rememberLazyListState(),
        // 只做展示：不接管手势，否则这块区域会把「点击歌词跳转」吃掉
        userScrollEnabled = false,
        // 不满 3 条时贴底排列：气泡始终紧挨评论条冒出，而不是浮在槽位顶部
        verticalArrangement = Arrangement.spacedBy(BubbleRowGap, Alignment.Bottom),
    ) {
        items(
            items = entries,
            key = { it.token },
        ) { entry ->
            CommentBubbleRow(
                entry = entry,
                authorColor = authorColor,
                contentColor = contentColor,
                onClick = onClick,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun CommentBubbleRow(
    entry: BubbleEntry,
    authorColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单条「冒出来」的动效：从下方 12dp 升起 + 淡入。key 稳定，只有新条会重放。
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 12.dp.toPx()
            }
            .height(BubbleRowHeight)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val author = entry.comment.authorName?.trim().orEmpty()
        if (author.isNotEmpty()) {
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = authorColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(
            text = entry.comment.content.trim().replace('\n', ' '),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    delay:Long=2000,
    activeLineColor: Color = Color(0xFF4CAF50),
    inactiveLineColor: Color = Color.Gray,
    activeLineFontSize: Int = 20,
    inactiveLineFontSize: Int = 16,
    userScrollEnabled: Boolean = true,
    autoScrollToCurrent: Boolean = true,
    showTranslation: Boolean = true,
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
            if (autoScrollToCurrent && currentHighlightIndex != - 1 && ! listState.isScrollInProgress && System.currentTimeMillis() >= nextAutoScrollableTime) {
                listState.animateScrollToItem(
                    currentHighlightIndex + 1,
                    - (maxHeight / 2f - (linesHeight[currentHighlightIndex] ?: 0f)).toInt()
                )
            }
        }
        LaunchedEffect(listState.isScrollInProgress) {
            if (! listState.isScrollInProgress) {
                delay(delay.milliseconds)
                nextAutoScrollableTime = System.currentTimeMillis() - 100
            }
        }
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部留白
            item {
                Spacer(modifier = Modifier.height(halfHeight.dp))
            }

            itemsIndexed(
                lyrics,
                key = { index, lyricLine -> "${lyricLine.startTimeMs}_$index" },
            ) { index, lyricLine ->
                val isActive = index == currentHighlightIndex
                BilingualLyricLineItem(
                    lyricLine = lyricLine,
                    isActive = isActive,
                    activeColor = activeLineColor,
                    inactiveColor = inactiveLineColor,
                    activeFontSize = activeLineFontSize,
                    inactiveFontSize = inactiveLineFontSize,
                    showTranslation = showTranslation,
                    onClick = {
                        onSeek(lyricLine.startTimeMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            if (linesHeight[index] == null) {
                                val height = px2dp(it.size.height.toFloat())
                                linesHeight[index] = height.toFloat()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = (activeLineFontSize / 3f).dp)
                )
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.height(halfHeight.dp))
            }
        }

        // 无歌词（或歌词无法按客户端正则解析）时给一句明确的空态：
        // 旧版是一片纯色空白，用户会以为「页面没加载出来」。
        if (lyrics.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂无歌词",
                    color = inactiveLineColor.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "换一首歌，或稍后再来看看",
                    color = inactiveLineColor.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
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
    showTranslation: Boolean = true,
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
            modifier = Modifier.fillMaxWidth(),
            lineHeight = activeFontSize.sp * 1.2f
        )

        // 翻译歌词（如果存在且开启）
        if (showTranslation && ! lyricLine.translatedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lyricLine.translatedText ?: "",
                color = if (isActive) activeColor.copy(alpha = 0.9f) else inactiveColor.copy(alpha = 0.7f),
                fontSize = if (isActive) (activeFontSize - 2).sp else (inactiveFontSize - 2).sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = activeFontSize.sp * 1.2f
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