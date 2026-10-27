package com.fox.music.core.model.music

/**
 * 歌词纠正的候选打分、截断与跨源去重。
 *
 * 每个来源只保留标题/艺人/时长最接近的几条，避免把搜索接口的原始长列表倒进界面。
 * 网易云比其他源更严（[NETEASE_LIMIT]），因为原先会按标题全等一直翻页。
 */
object LyricCandidateRanker {

    fun sourceLabel(raw: String?): String = when (raw?.lowercase()) {
        "netease" -> "网易云官方"
        "lrclib" -> "LRCLIB"
        "megalobiz" -> "Megalobiz"
        "lyricsovh" -> "lyrics.ovh"
        "custom" -> "自定义"
        else -> raw?.takeIf { it.isNotBlank() } ?: "未知来源"
    }

    const val NETEASE_FETCH = 8
    const val NETEASE_LIMIT = 3
    const val OPEN_LIMIT = 5
    const val MIN_SCORE = 0.45

    fun select(
        query: LyricQuery,
        hits: List<LyricHit>,
        limit: Int,
        requireLyrics: Boolean = true,
    ): List<ScoredLyricHit> {
        if (limit <= 0 || query.title.isBlank()) return emptyList()
        val scored = hits.mapNotNull { hit ->
            if (requireLyrics && usableLyrics(hit.lyrics).isBlank()) return@mapNotNull null
            val value = score(query, hit)
            if (value < MIN_SCORE) return@mapNotNull null
            ScoredLyricHit(hit, value)
        }.sortedByDescending { it.score }

        val seen = mutableSetOf<String>()
        val out = ArrayList<ScoredLyricHit>(minOf(limit, scored.size))
        for (item in scored) {
            if (requireLyrics) {
                val fingerprint = lyricFingerprint(item.hit.lyrics)
                if (fingerprint.length >= 16 && !seen.add(fingerprint)) continue
            }
            out += item
            if (out.size >= limit) break
        }
        return out
    }

    /** 跨来源合并。同一段歌词只留一条：分高优先，其次有翻译、有时间轴、来源优先级。 */
    fun merge(groups: List<List<ScoredLyricHit>>): List<ScoredLyricHit> {
        val out = mutableListOf<ScoredLyricHit>()
        for (group in groups) {
            for (item in group) {
                val fingerprint = lyricFingerprint(item.hit.lyrics)
                val index = if (fingerprint.length >= 16) {
                    out.indexOfFirst { lyricFingerprint(it.hit.lyrics) == fingerprint }
                } else {
                    -1
                }
                if (index >= 0) {
                    if (prefer(item, out[index])) out[index] = item
                } else {
                    out += item
                }
            }
        }
        return out.sortedByDescending { it.score }
    }

    fun dropMatching(items: List<ScoredLyricHit>, lyrics: String?): List<ScoredLyricHit> {
        if (lyrics.isNullOrBlank()) return items
        val fingerprint = lyricFingerprint(lyrics)
        if (fingerprint.length < 16) return items
        return items.filter { lyricFingerprint(it.hit.lyrics) != fingerprint }
    }

    fun score(query: LyricQuery, hit: LyricHit): Double {
        val title = textScore(query.title, hit.title)
        if (title < 0.35) return title
        val artist = artistScore(query.artist, hit.artist)
        val duration = durationScore(query.durationMs, hit.durationMs)
        return (title * 0.68 + artist * 0.22 + duration * 0.10).coerceIn(0.0, 1.0)
    }

    fun lyricFingerprint(lrc: String): String {
        return lrc.lineSequence()
            .map { line -> line.replace(Regex("""\[[^\]]*]|<[^>]*>"""), "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { it.lowercase() }
            .replace(Regex("""\s+"""), "")
            .take(480)
    }

    private fun usableLyrics(raw: String) = raw.trim()

    private fun prefer(candidate: ScoredLyricHit, current: ScoredLyricHit): Boolean {
        if (candidate.score > current.score + 0.02) return true
        if (current.score > candidate.score + 0.02) return false
        val candidateTrans = !candidate.hit.lyricsTrans.isNullOrBlank()
        val currentTrans = !current.hit.lyricsTrans.isNullOrBlank()
        if (candidateTrans != currentTrans) return candidateTrans
        if (candidate.hit.synced != current.hit.synced) return candidate.hit.synced
        return sourceRank(candidate.hit.source) < sourceRank(current.hit.source)
    }

    private fun sourceRank(source: String): Int = when (source.lowercase()) {
        "netease" -> 0
        "lrclib" -> 1
        "megalobiz" -> 2
        "lyricsovh" -> 3
        else -> 9
    }

    internal fun textScore(query: String, candidate: String): Double {
        val left = normalize(query)
        val right = normalize(candidate)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        if (left.length >= 2 && right.length >= 2 && (left.contains(right) || right.contains(left))) {
            val shorter = minOf(left.length, right.length)
            val longer = maxOf(left.length, right.length)
            val ratio = shorter.toDouble() / longer.toDouble()
            if (right.contains(left)) return (0.84 + 0.16 * ratio).coerceAtMost(1.0)
            if (ratio >= 0.72) return 0.7
            return 0.4
        }
        return dice(left, right)
    }

    private fun artistScore(query: String, candidate: String): Double {
        if (query.isBlank()) return 0.6
        if (candidate.isBlank()) return 0.45
        val left = splitArtists(query)
        val right = splitArtists(candidate)
        if (left.isEmpty() || right.isEmpty()) return textScore(query, candidate)
        return left.maxOf { part -> right.maxOf { other -> textScore(part, other) } }
    }

    private fun splitArtists(raw: String): List<String> =
        raw.split('/', '、', ',', '，', '＆', '&', '×')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun durationScore(queryMs: Long, hitMs: Long): Double {
        if (queryMs <= 0L || hitMs <= 0L) return 0.5
        val delta = kotlin.math.abs(queryMs - hitMs)
        return when {
            delta <= 5_000L -> 1.0
            delta <= 15_000L -> 0.7
            delta <= 30_000L -> 0.4
            else -> 0.1
        }
    }

    private fun normalize(raw: String): String {
        return raw.lowercase()
            .replace(Regex("""[（(\[【][^）)\]】]*[）)\]】]"""), "")
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""[^0-9a-z\u4e00-\u9fff]+"""), "")
    }

    private fun dice(left: String, right: String): Double {
        if (left.length < 2 || right.length < 2) return if (left == right) 1.0 else 0.0
        val leftGrams = bigrams(left)
        val rightGrams = bigrams(right)
        var intersection = 0
        for ((gram, count) in leftGrams) {
            intersection += minOf(count, rightGrams[gram] ?: 0)
        }
        val total = (left.length - 1) + (right.length - 1)
        if (total == 0) return 0.0
        return 2.0 * intersection / total
    }

    private fun bigrams(text: String): Map<String, Int> {
        val counts = HashMap<String, Int>(text.length)
        for (index in 0 until text.length - 1) {
            val gram = text.substring(index, index + 2)
            counts[gram] = (counts[gram] ?: 0) + 1
        }
        return counts
    }
}

data class LyricQuery(
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L,
) {
    /** 合作艺人只拿第一位去打外部搜索，整串留给相似度打分。 */
    fun primaryArtist(): String =
        artist.split('/', '、', ',', '，', '＆', '&', '×')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
}

data class LyricHit(
    val source: String,
    val sourceId: String? = null,
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val durationMs: Long = 0L,
    val lyrics: String = "",
    val lyricsTrans: String? = null,
    /** 展示用的来源页（Megalobiz 要求链回歌词页）。 */
    val attributionUrl: String? = null,
    /** 歌词正文的下载地址。搜索接口只给元数据时使用。 */
    val fetchUrl: String? = null,
    val synced: Boolean = true,
)

data class ScoredLyricHit(
    val hit: LyricHit,
    val score: Double,
)
