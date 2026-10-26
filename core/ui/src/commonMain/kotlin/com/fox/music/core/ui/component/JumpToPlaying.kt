package com.fox.music.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 正在播放的律动音条（全仓统一的「现在播放」视觉母题）。
 *
 * 容器高度由 [modifier] 决定，每根柱子按所在行的高度比例起伏；[bars] 控制柱子数量，
 * [isPlaying] 为 false 时柱子静止于低位（暂停态）。列表行的「正在播放」与播放队列的当前曲
 * 共用同一套语言，保证两处观感一致。
 */
@Composable
fun NowPlayingBars(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 3,
    isPlaying: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "nowPlayingBars")
    val bases = remember(bars) {
        val pattern = listOf(0.45f, 1f, 0.62f, 0.82f, 0.5f)
        List(bars) { pattern[it % pattern.size] }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(bars) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = androidx.compose.animation.core.StartOffset((i * 150) % 900),
                ),
                label = "npBar$i",
            )
            val wobble = 0.55f + 0.45f * abs(sin((phase + i * 0.33f) * 2.0 * PI)).toFloat()
            val fraction = if (isPlaying) (bases[i] * 0.45f + wobble * 0.55f) else 0.4f
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight(fraction.coerceIn(0.18f, 1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

/** 「跳到正在播放」按钮箭头的方向：目标在当前视区上方 → 向上箭头，下方 → 向下箭头。 */
enum class JumpDirection { Up, Down }

/**
 * 「跳到正在播放」悬浮按钮。
 *
 * 视觉上是一枚浮在右下角的圆形按钮，内部是一枚指示方向的箭头——目标曲目在视区上方就朝上、
 * 在下方就朝下，点明「点它能滚到正在播放那一行」。仅在 [visible] 为真时淡入并上滑出现。
 */
@Composable
fun JumpToPlayingFab(
    visible: Boolean,
    direction: JumpDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
    ) {
        Surface(
            onClick = onClick,
            color = scheme.surfaceContainerHigh,
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = if (direction == JumpDirection.Up) {
                        "向上跳到正在播放"
                    } else {
                        "向下跳到正在播放"
                    }
                },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (direction == JumpDirection.Up) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * 列表专用的「跳到正在播放」按钮。
 *
 * 内部把「是否可见 / 箭头方向」收进 [derivedStateOf]，只有跨过阈值（进入/离开视区、
 * 上下方向翻转）时才重组，避免整张列表随每一帧滚动重组。
 */
@Composable
fun JumpToPlayingFabForList(
    listState: LazyListState,
    targetIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible by remember(targetIndex) {
        derivedStateOf { targetIndex >= 0 && !listState.isIndexVisible(targetIndex) }
    }
    val direction by remember(targetIndex) {
        derivedStateOf {
            if (targetIndex >= 0) listState.jumpDirectionFor(targetIndex) else JumpDirection.Down
        }
    }
    JumpToPlayingFab(
        visible = visible,
        direction = direction,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * 全局「跳转高亮」控制器：点「跳到正在播放」后，让目标行闪一下强调色。
 *
 * 用 [token] 而不是单纯的 id 判断：连续两次跳到同一行时 id 不变、token 会自增，
 * 行内的 [jumpHighlight] 才能重新触发一次闪烁。
 */
class JumpHighlightController {
    var musicId by mutableStateOf<Long?>(null)
        private set
    var token by mutableIntStateOf(0)
        private set

    fun flash(id: Long?) {
        musicId = id
        token++
    }
}

/**
 * 全局「跳转高亮」信号，由 app 层在 NavHost 外层 provide（见 MainScreen）。
 * 未 provide 时默认实例不会变化，等价于「永不高亮」，iOS 外壳安全降级。
 */
val LocalJumpHighlight = compositionLocalOf { JumpHighlightController() }

/**
 * 命中 [musicId] 且刚被 [JumpHighlightController.flash] 触发时，在行内容之上叠一层
 * 强调色并快速淡出（约 700ms），用于「跳转到位」的视觉强调。
 *
 * 始终调用 [remember] / [LaunchedEffect]，不按 active 做条件分支，避免组合顺序错乱。
 */
@Composable
fun Modifier.jumpHighlight(musicId: Long): Modifier {
    val controller = LocalJumpHighlight.current
    val active = controller.musicId == musicId
    val token = controller.token
    val color = MaterialTheme.colorScheme.primary
    val anim = remember { Animatable(0f) }
    LaunchedEffect(token, active) {
        if (active) {
            anim.snapTo(1f)
            anim.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    return this.drawWithContent {
        drawContent()
        if (anim.value > 0.001f) {
            drawRect(color = color.copy(alpha = 0.16f * anim.value))
        }
    }
}

/** 当前 [index] 是否落在 LazyList 的可视项范围内（用于判断是否需要展示「跳到正在播放」）。 */
fun LazyListState.isIndexVisible(index: Int): Boolean {
    if (index < 0) return false
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return false
    return info.visibleItemsInfo.any { it.index == index }
}

/**
 * 目标 [index] 相对当前视区的方向：在第一个可见项之前 → 向上，否则 → 向下。
 * 用于「跳到正在播放」按钮的箭头朝向。
 */
fun LazyListState.jumpDirectionFor(index: Int): JumpDirection {
    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    return if (index < firstVisible) JumpDirection.Up else JumpDirection.Down
}

/**
 * 当前正在播放曲目的 id，全 App 唯一事实来源为 `MusicController.playerState`。
 *
 * 由 app 层在 NavHost 外层统一 provide（见 MainScreen），各列表页只读——
 * 这样「正在播放」高亮与「跳到正在播放」按钮无需把 musicController 逐层传参。
 * 用普通 [compositionLocalOf]（非 static），只有真正读取它的列表项会因切歌而重组。
 * 未 provide 时默认为 null（例如 iOS 外壳尚未接入时，仅表现为不高亮、不显示悬浮按钮）。
 */
val LocalNowPlayingMusicId = compositionLocalOf<Long?> { null }
