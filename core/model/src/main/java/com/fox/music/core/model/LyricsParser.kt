package com.fox.music.core.model

import kotlinx.serialization.Serializable

// LRC 歌词解析器
object LyricsParser {

    @Serializable
    data class LyricLine(
        val startTimeMs: Long,
        val text: String,
        val durationMs: Long = 0L
    )

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.+)""")

        lrcContent.lineSequence()
            .forEach { line ->
                val match = regex.find(line)
                if (match != null) {
                    val (min, sec, ms, text) = match.destructured
                    val startTimeMs = min.toLong() * 60 * 1000 +
                            sec.toLong() * 1000 +
                            ms.padEnd(3, '0').take(3).toLong()

                    lines.add(LyricLine(startTimeMs, text.trim()))
                }
            }

        // 计算每行持续时间
        for (i in lines.indices) {
            if (i < lines.size - 1) {
                lines[i] =
                    lines[i].copy(durationMs = lines[i + 1].startTimeMs - lines[i].startTimeMs)
            } else {
                lines[i] = lines[i].copy(durationMs = 5000) // 最后一行显示5秒
            }
        }

        return lines
    }

    // 查找当前时间对应的歌词
    fun findCurrentLyric(
        lyrics: List<LyricLine>,
        currentTimeMs: Long
    ): LyricLine? {
        return lyrics.lastOrNull { currentTimeMs >= it.startTimeMs }
    }

    fun findNextLyric(
        lyrics: List<LyricLine>,
        currentTimeMs: Long
    ): LyricLine? {
        return findCurrentLyric(lyrics, currentTimeMs)?.let {
            lyrics.indexOf(it)
        }.let {
            if ((it ?: -1) >= 0) {
                lyrics.getOrNull((it ?: -1) + 1)
            } else {
                null
            }
        }
    }
}