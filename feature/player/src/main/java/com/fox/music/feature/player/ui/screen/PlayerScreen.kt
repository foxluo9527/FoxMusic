package com.fox.music.feature.player.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.PlayerState
import com.fox.music.core.model.RepeatMode
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.components.CachedImage
import com.fox.music.core.ui.theme.Gray400
import com.fox.music.core.ui.theme.Gray700
import com.fox.music.feature.player.R

const val PLAYER_ROUTE = "player"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    musicController: MusicController,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onBack: () -> Unit = {},
    onFavoriteClick: (Long?) -> Unit = {},
) {
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val position by musicController.currentPosition.collectAsState(0L)
    with(sharedTransitionScope) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.Close, contentDescription = "Back",
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chat),
                        contentDescription = "comment",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                playerState.currentMusic?.let { music ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CachedImage(
                            imageUrl = music.coverImage,
                            contentDescription = music.title,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .sharedElement(
                                    sharedTransitionScope.rememberSharedContentState(key = "music-image-${music.id}"),
                                    animatedVisibilityScope = animatedContentScope
                                ),
                            shape = MaterialTheme.shapes.large
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = music.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "music-title-${music.id}"),
                                animatedVisibilityScope = animatedContentScope
                            )
                        )
                        Text(
                            text = music.artists.joinToString(", ") { it.name }
                                .ifEmpty { "Unknown" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "music-artist-${music.id}"),
                                animatedVisibilityScope = animatedContentScope
                            )
                        )
                        music.getCurrentLyric(playerState.position)?.let {
                            Text(
                                text = it,
                                color = Gray700,
                                maxLines = 1,
                                fontWeight = FontWeight(590),
                                fontSize = 17.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 30.dp)
                                    .sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(key = "music-lyric-${music.id}"),
                                        animatedVisibilityScope = animatedContentScope
                                    )
                            )
                        }
                        music.getNextLyric(playerState.position)?.let {
                            Text(
                                text = it,
                                color = Gray400,
                                fontWeight = FontWeight(400),
                                maxLines = 1,
                                fontSize = 15.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var nextPosition by remember { mutableFloatStateOf(0f) }
                val progress by remember(playerState, nextPosition) {
                    derivedStateOf {
                        if (nextPosition == 0f) {
                            playerState.position / (playerState.duration * 1f)
                        } else {
                            nextPosition / (playerState.duration * 1f)
                        }
                    }
                }
                Text(
                    text = formatMs(position),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = if (nextPosition == 0f) position.toFloat()
                        .coerceIn(
                            0f,
                            playerState.position.toFloat().coerceAtLeast(1f)
                        ) else nextPosition,
                    onValueChange = { nextPosition = it },
                    onValueChangeFinished = {
                        musicController.seekTo(nextPosition.toLong())
                        nextPosition = 0f
                    },
                    valueRange = 0f..(playerState.duration.toFloat().coerceAtLeast(1f)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    track = { state ->
                        Box(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (state.isDragging) 16.dp else 10.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(if (state.isDragging) 16.dp else 10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)
                                    )
                            )
                        }
                    },
                    thumb = {
                        Box(
                            Modifier
                                .padding(2.dp)
                                .background(Color.Gray, CircleShape)
                        ) {
                            Row(
                                Modifier
                                    .size(26.dp, 16.dp)
                                    .background(Color.White, CircleShape),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(
                                    Modifier
                                        .height(10.dp)
                                        .width(1.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(
                                    Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(10.dp)
                                        .width(1.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(
                                    Modifier
                                        .height(10.dp)
                                        .width(1.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                    }
                )
                Text(
                    text = formatMs(playerState.duration),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { musicController.setRepeatMode(nextRepeatMode(playerState.repeatMode)) }) {
                    Icon(
                        painter = painterResource(
                            when (playerState.repeatMode) {
                                RepeatMode.RANDOM -> R.drawable.ic_random
                                RepeatMode.ONE -> R.drawable.ic_single
                                RepeatMode.ALL -> R.drawable.ic_cycle
                            }
                        ),
                        contentDescription = "RepeatModel", modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { musicController.previous() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Previous",
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(
                    onClick = { if (playerState.isPlaying) musicController.pause() else musicController.play() },
                    modifier = Modifier.sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = "music-toggle-${playerState.currentMusic?.id}"),
                        animatedVisibilityScope = animatedContentScope
                    )
                ) {
                    Icon(
                        painterResource(if (playerState.isPlaying) R.drawable.iv_pause else R.drawable.iv_play),
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(
                    onClick = { musicController.next() },
                    modifier = Modifier.sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = "music-next-${playerState.currentMusic?.id}"),
                        animatedVisibilityScope = animatedContentScope
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Next",
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(
                    onClick = { onFavoriteClick(playerState.currentMusic?.id) },
                    modifier = Modifier.sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = "music-like-${playerState.currentMusic?.id}"),
                        animatedVisibilityScope = animatedContentScope
                    )
                ) {
                    Icon(
                        imageVector = if (playerState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (playerState.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (playerState.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.RANDOM -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.RANDOM
}
