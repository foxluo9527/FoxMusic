package com.fox.music.feature.player.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.theme.Gray400
import com.fox.music.core.ui.theme.Gray700
import com.fox.music.feature.player.R

/**
 *    Author : 罗福林
 *    Date   : 2026/2/4
 *    Desc   :
 */
@Composable
fun SharedTransitionScope.SongPage(
    modifier: Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    contrastColor: Color,
    animatedContentScope: AnimatedContentScope,
    onFavoriteClick: (Long?) -> Unit = {},
    onClickLyric: () -> Unit = {}
) {
    Column(
        modifier
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            playerState.currentMusic?.let {music ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .sharedElement(
                                rememberSharedContentState(key = "music-image-${music.id}"),
                                animatedVisibilityScope = animatedContentScope
                            ),
                        shape = MaterialTheme.shapes.large
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "music-title-${music.id}"),
                            animatedVisibilityScope = animatedContentScope
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = music.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = music.artists.joinToString(", ") {it.name}
                                .ifEmpty {"Unknown"},
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        Modifier.clickable{
                            onClickLyric()
                        }.sharedElement(
                            rememberSharedContentState(key = "music-lyric-${music.id}"),
                            animatedVisibilityScope = animatedContentScope
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
        }
        SongProgress(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            playerState,
            musicController,
            contrastColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {musicController.setRepeatMode(nextRepeatMode(playerState.repeatMode))}) {
                Icon(
                    painter = painterResource(
                        when(playerState.repeatMode) {
                            RepeatMode.RANDOM -> R.drawable.ic_random
                            RepeatMode.ONE -> R.drawable.ic_single
                            RepeatMode.ALL -> R.drawable.ic_cycle
                        }
                    ),
                    contentDescription = "RepeatModel", modifier = Modifier.size(28.dp),
                    tint = contrastColor
                )
            }
            IconButton(onClick = {musicController.previous()}) {
                Icon(
                    painter = painterResource(R.drawable.ic_previous),
                    contentDescription = "Previous",
                    modifier = Modifier.size(30.dp),
                    tint = contrastColor
                )
            }
            IconButton(
                onClick = {if (playerState.isPlaying) musicController.pause() else musicController.play()},
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "music-toggle-${playerState.currentMusic?.id}"),
                    animatedVisibilityScope = animatedContentScope
                )
            ) {
                Icon(
                    painterResource(if (playerState.isPlaying) R.drawable.iv_pause else R.drawable.iv_play),
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(30.dp),
                    tint = contrastColor
                )
            }
            IconButton(
                onClick = {musicController.next()},
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "music-next-${playerState.currentMusic?.id}"),
                    animatedVisibilityScope = animatedContentScope
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_next),
                    contentDescription = "Next",
                    modifier = Modifier.size(30.dp),
                    tint = contrastColor
                )
            }
            IconButton(
                onClick = {onFavoriteClick(playerState.currentMusic?.id)},
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "music-like-${playerState.currentMusic?.id}"),
                    animatedVisibilityScope = animatedContentScope
                )
            ) {
                Icon(
                    imageVector = if (playerState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (playerState.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (playerState.isFavorite) Color.Red else contrastColor,
                )
            }
        }
    }
}

private fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.RANDOM -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.RANDOM
}