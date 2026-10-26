package com.fox.music.core.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.theme.CoverShapeRow
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.util.formatTrackDuration

/**
 * 歌曲列表行（首页「为你精选」/ 搜索结果 / 歌单详情共用）。
 *
 * 2026-10 改版要点（原型 01 首页 .row2）：
 * - 行高锁 56dp、封面 44dp / 9dp 圆角：列表是"信息流"不是"作品墙"，封面要退到辅助位。
 * - 右侧补时长（等宽数字）：没有时长的列表行看起来像"没加载完"，这是最廉价的高级感。
 * - 正在播放态：标题转强调色 + 封面左下三根跳动柱，替代原来"完全没有状态"。
 * - ⋮ 从 48dp 触摸区收到 24dp 视觉盒：原来那颗图标把右侧留白吃掉，行尾很散。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicListItem(
    music: Music,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSelectionToggle: (() -> Unit)? = null,
    /** 该行是否为当前播放曲目（标题转强调色 + 跳动柱） */
    isNowPlaying: Boolean = false,
    /** 是否展示右侧时长（选择模式下自动隐藏） */
    showDuration: Boolean = true,
    /** 是否在本行顶部画 1dp 发丝分隔线（首行传 false） */
    showDivider: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val hairline = scheme.onSurface.copy(alpha = 0.07f)
    val durationText = remember(music.duration) { formatTrackDuration(music.duration) }

    with(sharedTransitionScope) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .drawBehind {
                    if (showDivider) {
                        // 分隔线从封面右缘起（44 + 12），与内容主轴对齐，而不是通栏
                        val startX = (44 + 12).dp.toPx()
                        drawLine(
                            color = hairline,
                            start = Offset(startX, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle?.invoke()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongClick,
                )
                .height(FoxSpacing.rowHeight)
                .jumpHighlight(music.id),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle?.invoke() },
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box(modifier = Modifier.size(44.dp)) {
                CachedImage(
                    imageUrl = music.coverImage,
                    contentDescription = music.title,
                    modifier = Modifier.size(44.dp),
                    shape = CoverShapeRow,
                )
                if (isNowPlaying && !isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, bottom = 4.dp),
                    ) {
                        NowPlayingBars(
                            color = scheme.primary,
                            bars = 3,
                            modifier = Modifier.height(9.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = music.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Medium,
                    color = if (isNowPlaying) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = music.artists.joinToString(", ") { it.name }.ifBlank { "未知艺人" },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!isSelectionMode && showDuration && durationText.isNotEmpty()) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }

            if (!isSelectionMode && onMoreClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onMoreClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
