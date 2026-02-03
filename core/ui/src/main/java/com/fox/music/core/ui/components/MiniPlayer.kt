package com.fox.music.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.Music
import com.fox.music.core.model.PlayerState
import com.fox.music.core.ui.theme.FoxMusicTheme

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val music = playerState.currentMusic ?: return
    with(sharedTransitionScope) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
        ) {
            Column {
                if (playerState.duration > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (playerState.position.toFloat() / playerState.duration).coerceIn(
                                0f,
                                1f
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .size(48.dp)
                            .sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "music-image-${music.id}"),
                                animatedContentScope
                            ),
                        shape = MaterialTheme.shapes.small,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = music.title + "-" + music.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "music-title-${music.id}"),
                                animatedContentScope
                            ),
                        )
                        Text(
                            text = music.getCurrentLyric(playerState.position) ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "music-lyric-${music.id}"),
                                animatedContentScope
                            ),
                        )
                    }
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.sharedElement(
                            sharedTransitionScope.rememberSharedContentState(key = "music-toggle-${playerState.currentMusic?.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.sharedElement(
                            sharedTransitionScope.rememberSharedContentState(key = "music-next-${music.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next track",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MiniPlayerPreview() {
    FoxMusicTheme {
        var count by remember { mutableStateOf(0) }
        SharedTransitionLayout {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { it } + fadeIn() togetherWith
                                slideOutVertically { -it } + fadeOut()
                    } else {
                        slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
            ) { targetCount ->
                MiniPlayer(
                    playerState = PlayerState(
                        currentMusic = Music(
                            id = targetCount.toLong(),
                            title = "Sample Song",
                            url = "",
                            duration = 240000,
                        ),
                        isPlaying = true,
                        position = 60000,
                        duration = 240000,
                    ),
                    this@SharedTransitionLayout,
                    this,
                    onPlayPauseClick = {},
                    onNextClick = {},
                )
            }
        }
    }
}
