package com.fox.music.core.player.lyric

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.domain.usecase.FetchMusicLyricsUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.controller.MusicControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听当前曲目变化，在歌词缺失时主动补全（列表接口未带歌词、或旧快照迁移缺字段时的兜底）。
 */
@OptIn(UnstableApi::class)
@Singleton
class LyricPrefetchCoordinator @Inject constructor(
    private val musicController: MusicControllerImpl,
    private val fetchMusicLyricsUseCase: FetchMusicLyricsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** 仅记录「已成功写入歌词」的曲目，失败可在再次切到该曲时重试。 */
    private var lastSuccessfulId: Long? = null
    private var inFlightJob: Job? = null
    private var inFlightMusicId: Long? = null

    fun start() {
        scope.launch {
            musicController.playerState
                .map { it.currentMusic }
                .distinctUntilChanged { old, new ->
                    old?.id == new?.id &&
                        old?.lyrics.isNullOrBlank() == new?.lyrics.isNullOrBlank()
                }
                .collect { music ->
                    if (music == null) {
                        lastSuccessfulId = null
                        inFlightJob?.cancel()
                        inFlightMusicId = null
                        return@collect
                    }
                    if (!shouldApplyPrefetchedLyrics(
                            hasOverride = musicController.hasLyricOverride(music.id),
                            currentLyrics = music.lyrics,
                        )
                    ) {
                        if (inFlightMusicId == music.id) {
                            inFlightJob?.cancel()
                            inFlightMusicId = null
                        }
                        lastSuccessfulId = music.id
                        return@collect
                    }
                    if (lastSuccessfulId == music.id) {
                        lastSuccessfulId = null
                    }
                    if (inFlightMusicId == music.id && inFlightJob?.isActive == true) return@collect
                    prefetchLyrics(music)
                }
        }
    }

    private fun prefetchLyrics(music: Music) {
        inFlightJob?.cancel()
        inFlightMusicId = music.id
        inFlightJob = scope.launch(Dispatchers.IO) {
            val lyrics = fetchMusicLyricsUseCase(music).getOrNull()
            if (lyrics == null || lyrics.lyrics.isBlank()) {
                Timber.d(
                    "Lyric prefetch missed musicId=%s thirdParty=%s sourceId=%s",
                    music.id,
                    music.isThirdParty,
                    music.sourceId,
                )
                return@launch
            }
            withContext(Dispatchers.Main) {
                // 切歌后结果过期则丢弃。用户已应用覆盖、或期间已经有歌词时，不用曲库结果盖掉。
                val current = musicController.playerState.value.currentMusic
                if (current?.id != music.id) return@withContext
                if (!shouldApplyPrefetchedLyrics(
                        hasOverride = musicController.hasLyricOverride(music.id),
                        currentLyrics = current.lyrics,
                    )
                ) {
                    return@withContext
                }
                musicController.fillMusicLyricsIfAbsent(
                    musicId = music.id,
                    lyrics = lyrics.lyrics,
                    lyricsTrans = lyrics.lyricsTrans,
                )
                lastSuccessfulId = music.id
            }
        }
    }
}
