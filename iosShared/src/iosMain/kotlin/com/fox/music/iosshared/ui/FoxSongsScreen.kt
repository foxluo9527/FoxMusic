package com.fox.music.iosshared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.iosshared.PlayableSong

/**
 * 用共享组件库（:core:ui）渲染的歌曲列表。
 * 主题与 Tab 渐变由 [com.fox.music.iosshared.foxComposeViewController] 统一包裹。
 */
@Composable
fun FoxSongsScreen(
    songs: List<Music>,
    playingId: String?,
    isPlaying: Boolean,
    onPlaySong: (PlayableSong) -> Unit,
    playableOf: (Music) -> PlayableSong?,
    title: String = "FoxMusic · 共享组件 core:ui",
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无歌曲", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            SharedTransitionLayout {
                AnimatedContent(targetState = songs.size, label = "songs") {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(songs, key = { it.id }) { music ->
                            val isCurrent = playingId != null && music.sourceId == playingId
                            MusicListItem(
                                music = music,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@AnimatedContent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isCurrent) {
                                            Modifier.background(
                                                if (isPlaying) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                onClick = {
                                    playableOf(music)?.let(onPlaySong)
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
