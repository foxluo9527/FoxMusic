package com.fox.music.core.player.lyric

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.player.controller.MusicControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class LyricPrefetchCoordinator @Inject constructor(
    private val musicController: MusicControllerImpl,
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastPrefetchedId: Long? = null

    fun start() {
        scope.launch {
            musicController.playerState
                .map { it.currentMusic }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { music ->
                    if (music == null) {
                        lastPrefetchedId = null
                        return@collect
                    }
                    if (!music.lyrics.isNullOrBlank()) {
                        lastPrefetchedId = music.id
                        return@collect
                    }
                    if (lastPrefetchedId == music.id) return@collect
                    lastPrefetchedId = music.id
                    prefetchLyrics(music.id)
                }
        }
    }

    private fun prefetchLyrics(musicId: Long) {
        scope.launch(Dispatchers.IO) {
            val detail = getMusicDetailUseCase(musicId).getOrNull()?.music ?: return@launch
            if (detail.lyrics.isNullOrBlank()) return@launch
            launch(Dispatchers.Main) {
                musicController.updateMusicLyrics(
                    musicId = musicId,
                    lyrics = detail.lyrics,
                    lyricsTrans = detail.lyricsTrans,
                )
            }
        }
    }
}
