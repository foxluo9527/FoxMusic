package com.fox.music.core.model.music

/**
 * 把用户粘贴的纯文本 / 不完整 LRC，以及公开源返回的纯文本，
 * 收成播放器能滚动的 LRC。
 *
 * 播放器只认带时间轴的行（见 [LyricsParser]）。纯文本会按曲目时长平均铺开，
 * 这样「歌词纠正」写回去之后歌词页能看到正文，而不是「暂无歌词」。
 */
object LyricTextNormalizer {

    data class PreparedLyrics(
        val ok: Boolean,
        val lrc: String = "",
        val lineCount: Int = 0,
        val message: String = "",
        /** false：原文没有时间轴，时间戳是按时长铺出来的。 */
        val synced: Boolean = false,
    )

    fun prepare(raw: String, durationMs: Long = 0L): PreparedLyrics {
        val cleaned = raw
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (cleaned.isEmpty()) return fail("歌词内容为空")
        if (looksLikeBinary(cleaned)) return fail("无法读取歌词文件，请选择 .lrc 或文本文件")

        val normalized = normalizeTimestampLines(cleaned)
        val timed = LyricsParser.parseLrc(normalized).filter { it.text.isNotBlank() }
        if (timed.isNotEmpty()) {
            val lrc = timed.joinToString("\n") { line ->
                "${formatTag(line.startTimeMs)}${line.text}"
            }
            return PreparedLyrics(ok = true, lrc = lrc, lineCount = timed.size, synced = true)
        }

        val plainLines = cleaned.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isMetadata(it) }
            .map { stripLeadingTimestamps(it).trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (plainLines.isEmpty()) return fail("没有可用的歌词正文")

        val lrc = synthesize(plainLines, durationMs)
        val count = LyricsParser.parseLrc(lrc).count { it.text.isNotBlank() }
        if (count == 0) return fail("没有可用的歌词正文")
        return PreparedLyrics(ok = true, lrc = lrc, lineCount = count, synced = false)
    }

    fun looksLikeBinary(text: String): Boolean {
        if (text.isEmpty()) return false
        val sample = text.take(2000)
        val bad = sample.count { ch ->
            ch == '\u0000' || (ch.code < 32 && ch != '\n' && ch != '\r' && ch != '\t')
        }
        return bad * 20 > sample.length
    }

    private fun fail(message: String) = PreparedLyrics(ok = false, message = message)

    private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTime = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val metadata = Regex("""(?i)^\[(ti|ar|al|by|offset|length|re|ve|tool|id|hash|au|la):.*]$""")

    private fun isMetadata(line: String) = metadata.matches(line.trim())

    private fun normalizeTimestampLines(raw: String): String {
        return raw.lineSequence().flatMap { line ->
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) return@flatMap listOf(line)
            val text = line.substring(matches.last().range.last + 1)
                .replace(wordTime, "")
                .trim()
            matches.map { match -> "[${formatParts(match)}]$text" }
        }.joinToString("\n")
    }

    private fun formatParts(match: MatchResult): String {
        val min = match.groupValues[1].toIntOrNull()?.coerceIn(0, 599) ?: 0
        val sec = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
        val frac = match.groupValues[3]
        val fracOut = when (frac.length) {
            0 -> "00"
            1 -> "${frac}0"
            2 -> frac
            else -> frac.take(3)
        }
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.$fracOut"
    }

    private fun stripLeadingTimestamps(line: String): String {
        var rest = line
        while (true) {
            val match = timestamp.find(rest) ?: break
            if (match.range.first != 0) break
            rest = rest.substring(match.range.last + 1).trimStart()
        }
        return rest
    }

    private fun synthesize(lines: List<String>, durationMs: Long): String {
        val duration = if (durationMs >= 3_000L) durationMs else lines.size * 4_000L
        val step = (duration / lines.size).coerceAtLeast(800L)
        return lines.mapIndexed { index, text ->
            "${formatTag(index * step)}$text"
        }.joinToString("\n")
    }

    private fun formatTag(timeMs: Long): String {
        val total = timeMs.coerceAtLeast(0)
        val min = total / 60_000
        val sec = (total % 60_000) / 1000
        val hundredths = (total % 1000) / 10
        return "[${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.${hundredths.toString().padStart(2, '0')}]"
    }
}
