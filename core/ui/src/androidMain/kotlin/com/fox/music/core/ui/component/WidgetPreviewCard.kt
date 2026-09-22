package com.fox.music.core.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.PlayerState

enum class WidgetPreviewSize {
    Small,
    Medium,
    Large,
}

@Composable
fun WidgetPreviewCard(
    playerState: PlayerState,
    modifier: Modifier = Modifier,
) {
    var selectedSize by remember { mutableStateOf(WidgetPreviewSize.Medium) }
    val music = playerState.currentMusic
    val position = playerState.position
    val colors = MaterialTheme.colorScheme
    val lyricColors = rememberLyricColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = "小组件预览",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetPreviewSize.entries.forEach { size ->
                FilterChip(
                    selected = selectedSize == size,
                    onClick = { selectedSize = size },
                    label = {
                        Text(
                            when (size) {
                                WidgetPreviewSize.Small -> "迷你 2×2"
                                WidgetPreviewSize.Medium -> "标准 4×2"
                                WidgetPreviewSize.Large -> "大卡 4×4"
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    when (selectedSize) {
                        WidgetPreviewSize.Small -> 132.dp
                        WidgetPreviewSize.Medium -> 118.dp
                        WidgetPreviewSize.Large -> 300.dp
                    },
                )
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface),
        ) {
            when (selectedSize) {
                WidgetPreviewSize.Small -> WidgetPreviewSmall(playerState)
                WidgetPreviewSize.Medium -> WidgetPreviewMedium(playerState, position, colors, lyricColors)
                WidgetPreviewSize.Large -> WidgetPreviewLarge(playerState, position, lyricColors)
            }
        }
        if (music == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "播放音乐后可预览封面与歌词信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetPreviewSmall(playerState: PlayerState) {
    val music = playerState.currentMusic
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
    ) {
        WidgetPreviewInfoRow(
            title = music?.title ?: "FoxMusic",
            artist = music?.artists?.joinToString { it.name } ?: "暂无播放",
            coverUrl = music?.coverImage,
            coverSize = 48.dp,
            titleArtistCombined = false,
            showLyric = false,
            currentLyric = null,
        )
        Spacer(Modifier.height(8.dp))
        WidgetPreviewControls(playerState, showRepeatAndFavorite = false)
    }
}

@Composable
private fun WidgetPreviewMedium(
    playerState: PlayerState,
    position: Long,
    colors: androidx.compose.material3.ColorScheme,
    lyricColors: com.fox.music.core.common.lyric.LyricColors,
) {
    val music = playerState.currentMusic
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        WidgetPreviewInfoRow(
            title = music?.title ?: "FoxMusic",
            artist = music?.artists?.joinToString { it.name } ?: "暂无播放",
            coverUrl = music?.coverImage,
            coverSize = 52.dp,
            titleArtistCombined = true,
            showLyric = true,
            currentLyric = music?.getCurrentLyric(position) ?: "暂无歌词",
            lyricColor = lyricColors.highlightComposeColor(),
        )
        Spacer(Modifier.height(8.dp))
        WidgetPreviewControls(playerState, showRepeatAndFavorite = true)
    }
}

@Composable
private fun WidgetPreviewLarge(
    playerState: PlayerState,
    position: Long,
    lyricColors: com.fox.music.core.common.lyric.LyricColors,
) {
    val music = playerState.currentMusic
    val lines = music?.lyricLines?.map { it.text }.orEmpty()
    val currentIndex = music?.lyricLines
        ?.indexOfLast { it.startTimeMs <= position }
        ?.coerceAtLeast(0) ?: 0
    val start = (currentIndex - 2).coerceAtLeast(0)
    val previewLines = if (lines.isEmpty()) {
        listOf("暂无歌词")
    } else {
        lines.drop(start).take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        WidgetPreviewInfoRow(
            title = music?.title ?: "FoxMusic",
            artist = music?.artists?.joinToString { it.name } ?: "暂无播放",
            coverUrl = music?.coverImage,
            coverSize = 64.dp,
            titleArtistCombined = false,
            showArtist = true,
            showLyric = false,
            currentLyric = null,
        )
        Spacer(Modifier.height(12.dp))
        previewLines.forEachIndexed { index, line ->
            val isCurrent = lines.isNotEmpty() && start + index == currentIndex
            Text(
                text = line,
                color = if (isCurrent) {
                    lyricColors.highlightComposeColor()
                } else {
                    lyricColors.textComposeColor()
                },
                fontSize = if (isCurrent) 15.sp else 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = if (isCurrent) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (index != previewLines.lastIndex) Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        WidgetPreviewControls(playerState, showRepeatAndFavorite = true)
    }
}

@Composable
private fun WidgetPreviewInfoRow(
    title: String,
    artist: String,
    coverUrl: String?,
    coverSize: androidx.compose.ui.unit.Dp,
    titleArtistCombined: Boolean,
    showArtist: Boolean = true,
    showLyric: Boolean,
    currentLyric: String?,
    lyricColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetPreviewCover(
            title = title,
            coverUrl = coverUrl,
            size = coverSize,
            corner = 12.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (titleArtistCombined) "$title - $artist" else title,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showLyric) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentLyric ?: "暂无歌词",
                    color = lyricColor ?: colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!titleArtistCombined && showArtist && artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = artist,
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WidgetPreviewControls(
    playerState: PlayerState,
    showRepeatAndFavorite: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    PlaybackControlBar(
        playerState = playerState,
        onRepeatClick = {},
        onPreviousClick = {},
        onTogglePlayClick = {},
        onNextClick = {},
        onFavoriteClick = {},
        iconTint = colors.onSurface,
        favoriteTint = if (playerState.isFavorite) colors.primary else colors.onSurface,
        showRepeatAndFavorite = showRepeatAndFavorite,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WidgetPreviewCover(
    title: String?,
    coverUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner)),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            CachedImage(
                imageUrl = coverUrl,
                contentDescription = title,
                modifier = Modifier.size(size),
                shape = RoundedCornerShape(corner),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title?.take(1) ?: "♪",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.28f).sp,
                )
            }
        }
    }
}
