package com.fox.music.feature.stream

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.music.Artist
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
     *
     * 注意：服务端 `/api/highlight/stream` 只返回拼接好的歌手字符串（多艺人以 `/` 相连），
     * 而全 App 展示歌手（播放页 / 播放列表 / 锁屏 / 通知 / 桌面小组件 / 车载）统一读
     * `Music.artists`，这里若不回填就会在音流页外的所有页面显示 "Unknown"。
     * 音流页自身仍用 [StreamItemUi.artist] 的原始拼接串，保持与接口文本一致。
     */
    fun buildPlaylist(items: List<HighlightQueueItem>): List<StreamItemUi> {
        return items.map { item ->
            val artistName = item.artist?.trim()?.takeIf { it.isNotBlank() }
            StreamItemUi(
                music = Music(
                    id = item.musicId,
                    title = item.title ?: "",
                    url = item.url ?: "",
                    // 接口返回的是 /uploads/... 相对路径，必须过 MediaUrlResolver 才能被 Coil 加载
                    coverImage = MediaUrlResolver.resolve(item.coverUrl),
                    duration = item.duration ?: 0L,
                    // 音流没有艺人 id，占位 0：展示只依赖 name，艺人收藏走按名检索
                    artists = artistName
                        ?.let { listOf(Artist(id = 0L, name = it)) }
                        .orEmpty(),
                ),
                clips = item.clips.map { toClipRange(it) },
                artist = artistName,
            )
        }
    }
}
