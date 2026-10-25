package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.theme.foxEditorialAccent

@Composable
fun PostMusicBar(
    music: Music,
    onPlayClick: () -> Unit,
    onClick: () -> Unit = {},
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = foxEditorialAccent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            // v11：容器色一律预混实色，禁用 alpha 叠加 —— 半透明容器合成到不同底色上
            // 会得到不同颜色（脏粉 / 脏蓝），完全不可控。
            .background(
                if (isCurrentTrack) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = buildString {
                append(music.title)
                val artist = music.artists.joinToString("/") { it.name }
                if (artist.isNotBlank()) {
                    append(" - ")
                    append(artist)
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        // 命中区 44dp（图标本身 20dp），强调色只落在播放键上
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onPlayClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isCurrentTrack && isPlaying) {
                    Icons.Default.Pause
                } else {
                    Icons.Default.PlayArrow
                },
                contentDescription = if (isCurrentTrack && isPlaying) "暂停" else "播放",
                modifier = Modifier.size(20.dp),
                tint = accent,
            )
        }
    }
}

@Composable
fun PostMusicBarExtended(
    music: Music,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onRowClick: () -> Unit = onPlayClick,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = buildString {
                append(music.title)
                val artist = music.artists.joinToString("/") { it.name }
                if (artist.isNotBlank()) {
                    append(" - ")
                    append(artist)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (music.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "收藏音乐",
                tint = if (music.isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAddToPlaylistClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "加入歌单",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlayClick) {
            Icon(
                imageVector = if (isCurrentTrack && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isCurrentTrack && isPlaying) "暂停" else "播放",
            )
        }
    }
}
