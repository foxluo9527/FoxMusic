package com.fox.music.core.model.music

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * 客户端直接调用的公开歌词接口解析。
 *
 * 选用（均无 key，原生客户端无 CORS 问题，不必再经 FoxResourceGroup 代理）：
 * - LRCLIB `GET /api/search`：https://lrclib.net/docs ，匿名可用，需带应用名 User-Agent。
 * - Megalobiz `GET /api/public/v1/search` + `/lrc/{id}`：https://megalobiz.com/developers ，
 *   文档写明个人/非商业，展示时要带回来源页。
 * - lyrics.ovh `GET /v1/{artist}/{title}`：https://github.com/NTag/lyrics.ovh ，纯文本、只返回一首。
 *   服务端刮削本来就在用它，这里改由客户端直连，避免再绕一跳。
 *
 * 不接：QQ / 酷狗 / 酷我（无公开授权、条款限制转载）、lrc.cx 公共实例（文档写明走酷狗）、
 * Musixmatch / Genius（项目没有 key，且限制存储歌词）。
 */
object LyricSourceParsers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseLrclib(body: String): List<LyricHit> {
        val array = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if (obj.bool("instrumental")) return@mapNotNull null
            val title = obj.text("trackName") ?: obj.text("name") ?: return@mapNotNull null
            val artist = obj.text("artistName").orEmpty()
            val durationMs = ((obj.dbl("duration") ?: 0.0) * 1000.0).toLong()
            val syncedRaw = obj.text("syncedLyrics")
            val plainRaw = obj.text("plainLyrics")
            val raw = syncedRaw ?: plainRaw ?: return@mapNotNull null
            val prepared = LyricTextNormalizer.prepare(raw, durationMs)
            if (!prepared.ok) return@mapNotNull null
            LyricHit(
                source = "lrclib",
                sourceId = obj.text("id"),
                title = title,
                artist = artist,
                album = obj.text("albumName"),
                durationMs = durationMs,
                lyrics = prepared.lrc,
                synced = prepared.synced,
            )
        }
    }

    fun parseMegalobizSearch(body: String): List<LyricHit> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val results = root["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.text("title") ?: return@mapNotNull null
            val fetchUrl = obj.text("lrc_url") ?: return@mapNotNull null
            val durationMs = ((obj.dbl("duration") ?: 0.0) * 1000.0).toLong()
            LyricHit(
                source = "megalobiz",
                sourceId = obj.text("id"),
                title = title,
                artist = obj.text("artist").orEmpty(),
                durationMs = durationMs,
                attributionUrl = obj.text("url"),
                fetchUrl = fetchUrl,
                synced = true,
            )
        }
    }

    fun parseLyricsOvh(body: String): String? {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        return root.text("lyrics")
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.dbl(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
            ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
}
