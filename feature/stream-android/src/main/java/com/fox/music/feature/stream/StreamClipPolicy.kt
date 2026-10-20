package com.fox.music.feature.stream

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.music.ClipRange
import com.fox.music.core.model.music.Music
import com.fox.music.core.network.model.HighlightClipDto
import com.fox.music.core.network.model.HighlightQueueItem

/**
 * 音流片段策略：服务端片段 DTO → 播放器 ClipRange 映射，以及无片段时的兜底 clip。
 * 兜底口径与 core:player MusicControllerImpl.fallbackClip 完全一致（取曲长前 30% 起、长 60s，
 * 与服务端 HIGHLIGHT_CLIP_SEC=60 对齐——汽水音乐式一分钟片段，短曲不足则放到整曲）。
 */
object StreamClipPolicy {

    fun fallbackClip(music: Music): ClipRange {
        val dur = when {
            music.duration <= 0L -> 210_000L
            music.duration < 1000L -> music.duration * 1000L
            else -> music.duration
        }
        val safeDur = dur.coerceIn(30_000L, 600_000L)
        val start = ((safeDur * 0.3).toLong()).coerceAtMost((safeDur - 60_000L).coerceAtLeast(0L))
        val end = (start + 60_000L).coerceAtMost(safeDur)
        return ClipRange(start, end)
    }

    fun toClipRange(dto: HighlightClipDto): ClipRange =
        ClipRange(startMs = dto.startMs, endMs = dto.endMs, label = dto.label)

    /**
     * 由服务端队列项构造音流 UI 列表（每首歌 + 其高潮片段 + 歌手名）。
     */
    fun buildPlaylist(items: List<HighlightQueueItem>): List<StreamItemUi> {
        return items.map { item ->
            StreamItemUi(
                music = Music(
                    id = item.musicId,
                    title = item.title ?: "",
                    url = item.url ?: "",
                    // 接口返回的是 /uploads/... 相对路径，必须过 MediaUrlResolver 才能被 Coil 加载
                    coverImage = MediaUrlResolver.resolve(item.coverUrl),
                    duration = item.duration ?: 0L,
                ),
                clips = item.clips.map { toClipRange(it) },
                artist = item.artist,
            )
        }
    }
}
