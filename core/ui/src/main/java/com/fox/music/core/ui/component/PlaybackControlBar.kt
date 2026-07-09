package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fox.music.core.common.R
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode

@Composable
fun PlaybackControlBar(
    playerState: PlayerState,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onTogglePlayClick: () -> Unit,
    onNextClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    favoriteTint: Color = if (playerState.isFavorite) Color(0xFFFF5252) else iconTint,
    showRepeatAndFavorite: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRepeatAndFavorite) {
            IconButton(onClick = onRepeatClick) {
                Icon(
                    painter = painterResource(
                        when (playerState.repeatMode) {
                            RepeatMode.SEQUENTIAL -> R.drawable.ic_sequential
                            RepeatMode.ALL -> R.drawable.ic_cycle
                            RepeatMode.ONE -> R.drawable.ic_single
                            RepeatMode.RANDOM -> R.drawable.ic_random
                        },
                    ),
                    contentDescription = "播放模式",
                    modifier = Modifier.size(28.dp),
                    tint = iconTint,
                )
            }
        }
        IconButton(onClick = onPreviousClick) {
            Icon(
                painter = painterResource(R.drawable.ic_previous),
                contentDescription = "上一首",
                modifier = Modifier.size(30.dp),
                tint = iconTint,
            )
        }
        IconButton(onClick = onTogglePlayClick) {
            if (playerState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = iconTint,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (playerState.isPlaying) R.drawable.iv_pause else R.drawable.iv_play,
                    ),
                    contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(30.dp),
                    tint = iconTint,
                )
            }
        }
        IconButton(onClick = onNextClick) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = "下一首",
                modifier = Modifier.size(30.dp),
                tint = iconTint,
            )
        }
        if (showRepeatAndFavorite) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    painter = painterResource(
                        if (playerState.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    ),
                    contentDescription = if (playerState.isFavorite) "取消收藏" else "收藏",
                    modifier = Modifier.size(28.dp),
                    tint = favoriteTint,
                )
            }
        }
    }
}

fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.SEQUENTIAL -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.RANDOM
    RepeatMode.RANDOM -> RepeatMode.SEQUENTIAL
}
