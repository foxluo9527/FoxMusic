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
import com.fox.music.core.ui.theme.FoxMusicTheme
import com.fox.music.iosshared.PlayableSong

/**
 * 用**共享组件库**（:core:ui）渲染的歌曲列表 —— 与 Android 端同一份代码。
 *
 * 批次 9 的关键验证点：不再是批次 8 那种在 iosShared 里手写的 demo UI，
 * 而是直接复用 Android 端在用的 FoxMusicTheme / MusicListItem / CachedImage。
 * CachedImage 内部的图片加载在 iOS 上走 Ktor + Skia 解码（见 core:ui 的 iosMain）。
 */
@Composable
fun FoxSongsScreen(
    songs: List<Music>,
    playingId: String?,
    isPlaying: Boolean,
    onPlaySong: (PlayableSong) -> Unit,
    playableOf: (Music) -> PlayableSong?,
) {
    FoxMusicTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Text(
                text = "FoxMusic · 共享组件 core:ui",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // MusicListItem 需要共享元素动画作用域，这里提供最小宿主
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
}
