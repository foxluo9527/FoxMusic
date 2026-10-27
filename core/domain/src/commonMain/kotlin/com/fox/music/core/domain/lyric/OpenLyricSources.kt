package com.fox.music.core.domain.lyric

import com.fox.music.core.model.music.LyricCandidateRanker
import com.fox.music.core.model.music.LyricHit
import com.fox.music.core.model.music.LyricQuery
import com.fox.music.core.model.music.LyricSourceParsers
import com.fox.music.core.model.music.LyricTextNormalizer
import com.fox.music.core.model.music.ScoredLyricHit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 歌词纠正用的公开歌词源。客户端直连，不经过 FoxResourceGroup。
 *
 * 网易云仍走现有 `/api/music/third-party/search`（官方接口要加密参数，客户端直连不稳）。
 * 这里只接文档写明可匿名使用的源，每个源在返回前已按相似度截到前几条。
 */
class OpenLyricSources(
    private val http: OpenLyricHttp,
) {
    suspend fun lrclib(query: LyricQuery): List<ScoredLyricHit> {
        if (query.title.isBlank()) return emptyList()
        val url = buildString {
            append("https://lrclib.net/api/search?track_name=")
            append(urlEncode(query.title))
            val artist = query.primaryArtist()
            if (artist.isNotBlank()) {
                append("&artist_name=")
                append(urlEncode(artist))
            }
        }
        val body = getOk(url, "LRCLIB") ?: return emptyList()
        return LyricCandidateRanker.select(
            query,
            LyricSourceParsers.parseLrclib(body),
            LyricCandidateRanker.OPEN_LIMIT,
        )
    }

    suspend fun megalobiz(query: LyricQuery): List<ScoredLyricHit> {
        if (query.title.isBlank()) return emptyList()
        val url = buildString {
            append("https://api.megalobiz.com/api/public/v1/search?title=")
            append(urlEncode(query.title))
            val artist = query.primaryArtist()
            if (artist.isNotBlank()) {
                append("&artist=")
                append(urlEncode(artist))
            }
            if (query.durationMs > 0L) {
                append("&duration=")
                append(query.durationMs / 1000L)
            }
            append("&limit=12")
        }
        val body = getOk(url, "Megalobiz") ?: return emptyList()
        val ranked = LyricCandidateRanker.select(
            query,
            LyricSourceParsers.parseMegalobizSearch(body),
            LyricCandidateRanker.OPEN_LIMIT,
            requireLyrics = false,
        )
        val fetched = coroutineScope {
            ranked.map { stub ->
                async { fetchMegalobizLrc(query, stub.hit) }
            }.awaitAll().filterNotNull()
        }
        return LyricCandidateRanker.select(query, fetched, LyricCandidateRanker.OPEN_LIMIT)
    }

    suspend fun lyricsOvh(query: LyricQuery): List<ScoredLyricHit> {
        val artist = query.primaryArtist()
        if (query.title.isBlank() || artist.isBlank()) return emptyList()
        val url = "https://api.lyrics.ovh/v1/${urlEncode(artist)}/${urlEncode(query.title)}"
        val body = getOk(url, "lyrics.ovh") ?: return emptyList()
        val text = LyricSourceParsers.parseLyricsOvh(body) ?: return emptyList()
        val prepared = LyricTextNormalizer.prepare(text, query.durationMs)
        if (!prepared.ok) return emptyList()
        return LyricCandidateRanker.select(
            query,
            listOf(
                LyricHit(
                    source = "lyricsovh",
                    title = query.title,
                    artist = artist,
                    durationMs = query.durationMs,
                    lyrics = prepared.lrc,
                    synced = prepared.synced,
                )
            ),
            limit = 1,
        )
    }

    private suspend fun fetchMegalobizLrc(query: LyricQuery, stub: LyricHit): LyricHit? {
        val fetchUrl = stub.fetchUrl ?: return null
        if (!fetchUrl.startsWith("https://") && !fetchUrl.startsWith("http://")) return null
        val body = getOk(fetchUrl, "Megalobiz") ?: return null
        val prepared = LyricTextNormalizer.prepare(body, stub.durationMs.takeIf { it > 0 } ?: query.durationMs)
        if (!prepared.ok) return null
        return stub.copy(lyrics = prepared.lrc, synced = prepared.synced, fetchUrl = null)
    }

    /** 404 当没找到；其他非 2xx 抛出，调用方记成「该来源暂不可用」。 */
    private suspend fun getOk(url: String, source: String): String? {
        val response = http.getText(url, HEADERS)
        if (response.code == 404) return null
        if (response.code !in 200..299) error("$source HTTP ${response.code}")
        return response.body
    }

    companion object {
        const val USER_AGENT = "FoxMusic (https://github.com/foxluo9527/FoxMusic)"
        val HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
        )
    }
}

interface OpenLyricHttp {
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): OpenLyricResponse
}

data class OpenLyricResponse(
    val code: Int,
    val body: String,
)

internal fun urlEncode(value: String): String = buildString(value.length * 3) {
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        when (b) {
            in 'a'.code..'z'.code,
            in 'A'.code..'Z'.code,
            in '0'.code..'9'.code,
            '-'.code, '_'.code, '.'.code, '~'.code -> append(b.toChar())
            else -> {
                append('%')
                val hex = b.toString(16).uppercase()
                if (hex.length == 1) append('0')
                append(hex)
            }
        }
    }
}
