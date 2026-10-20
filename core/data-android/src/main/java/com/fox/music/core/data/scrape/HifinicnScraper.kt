package com.fox.music.core.data.scrape

import com.fox.music.core.data.importqueue.ImportLog
import com.fox.music.core.domain.repository.ScrapeConfig
import com.fox.music.core.domain.repository.ScrapeSite
import com.fox.music.core.domain.repository.ScrapeStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class DetailCandidate(
    val title: String,
    val href: String,
    val score: Int,
)

data class ResolvedPlayable(
    val playUrl: String,
    val detailUrl: String?,
    val referer: String?,
    val source: String,
    val matchScore: Int,
    val titleRaw: String?,
)

/**
 * 多站音源逆向（导入解析）：
 * 四站并行；任一站满分早停；否则等全部结果再按分排名。
 * 不以 hifinicn 为特权源。
 */
@Singleton
class HifinicnScraper @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 导入用：多站并行，不以 hifinicn 为特权源。
     * - 任一站满分可播（score>=95）→ 立即采纳并取消其余
     * - 否则等全部出齐，按 matchScore 排名取最优
     * Cookie 来自服务端 scrape-config，客户端本地带 Cookie 解析详情（含 getmusic.htm）。
     */
    suspend fun resolveForImport(
        config: ScrapeConfig,
        artist: String,
        songName: String,
        excludedDetailUrls: Set<String> = emptySet(),
        onRejectedDetail: ((String) -> Unit)? = null,
    ): ResolvedPlayable? = withContext(Dispatchers.IO) {
        val siteKeys = buildList {
            if (config.sites.containsKey("hifinicn")) add("hifinicn")
            FALLBACK_SITES.filterTo(this) { config.sites.containsKey(it) }
        }
        if (siteKeys.isEmpty()) {
            ImportLog.w("解析: 无可用站点配置")
            return@withContext null
        }
        ImportLog.i(
            "解析开始: 「$artist - $songName」并行站点=${siteKeys.joinToString()} " +
                "排除详情=${excludedDetailUrls.size}",
        )

        coroutineScope {
            val settled = AtomicBoolean(false)
            val winner = AtomicReference<ResolvedPlayable?>(null)
            val perfectGate = CompletableDeferred<ResolvedPlayable>()

            fun tryFinish(result: ResolvedPlayable?, reason: String): Boolean {
                if (result == null) return false
                if (!settled.compareAndSet(false, true)) return false
                winner.set(result)
                ImportLog.i(
                    "评选结算($reason): source=${result.source} score=${result.matchScore} " +
                        "title=${result.titleRaw} detail=${result.detailUrl}",
                )
                return true
            }

            val jobs = siteKeys.map { siteKey ->
                async {
                    if (settled.get() || perfectGate.isCompleted) return@async null
                    ImportLog.i("站点开始: $siteKey 「$artist - $songName」")
                    val hit = resolveSiteAllStrategies(
                        config = config,
                        siteKey = siteKey,
                        artist = artist,
                        songName = songName,
                        excludedDetailUrls = excludedDetailUrls,
                        onRejectedDetail = onRejectedDetail,
                        isCancelled = { settled.get() || perfectGate.isCompleted },
                    )
                    if (hit != null) {
                        ImportLog.i(
                            "站点命中: $siteKey score=${hit.matchScore} " +
                                "title=${hit.titleRaw} ${if (isPerfectHit(hit)) "【满分】" else ""}",
                        )
                    } else {
                        ImportLog.w("站点未命中: $siteKey")
                    }
                    if (hit != null && isPerfectHit(hit)) {
                        perfectGate.complete(hit)
                    }
                    hit
                }
            }

            select {
                perfectGate.onAwait { perfect ->
                    if (tryFinish(perfect, "满分早停")) {
                        jobs.forEach { it.cancel() }
                        ImportLog.i("评选: 取消其余站点任务")
                    }
                }
                async {
                    jobs.map { runCatching { it.await() }.getOrNull() }
                }.onAwait { allHits ->
                    if (settled.get()) return@onAwait
                    val hits = allHits.filterNotNull()
                    ImportLog.i(
                        "评选: 全站结束 可播结果=${hits.size} " +
                            hits.joinToString(" | ") { "${it.source}:${it.matchScore}:${it.titleRaw}" },
                    )
                    val perfect = hits.firstOrNull { isPerfectHit(it) }
                    if (perfect != null) {
                        tryFinish(perfect, "全站后满分")
                        return@onAwait
                    }
                    val best = rankBest(hits)
                    tryFinish(best, "按分排名")
                    if (best == null) settled.set(true)
                }
            }

            val allHits = jobs.map { runCatching { it.await() }.getOrNull() }
            if (!settled.get()) {
                val best = rankBest(allHits.filterNotNull())
                if (best != null) tryFinish(best, "兜底排名") else {
                    settled.set(true)
                    ImportLog.w("评选: 全部失败，无可用结果")
                }
            }

            winner.get()
        }
    }

    private fun rankBest(hits: List<ResolvedPlayable>): ResolvedPlayable? =
        hits.maxWithOrNull(
            compareByDescending<ResolvedPlayable> { it.matchScore }
                .thenBy { it.titleRaw?.length ?: Int.MAX_VALUE },
        )

    private suspend fun resolveSiteAllStrategies(
        config: ScrapeConfig,
        siteKey: String,
        artist: String,
        songName: String,
        excludedDetailUrls: Set<String>,
        onRejectedDetail: ((String) -> Unit)?,
        isCancelled: () -> Boolean,
    ): ResolvedPlayable? {
        val site = config.sites[siteKey] ?: return null
        val strategies = site.strategies.ifEmpty {
            listOf(
                ScrapeStrategy(
                    key = siteKey,
                    searchPath = site.searchPath,
                    searchKeywordFormat = if (siteKey == "hifinicn") "{artist}-{songName}" else "{artist}《{songName}",
                    maxPage = 1,
                    resultLinkSelector = site.resultLinkSelector,
                    requireQualityTag = siteKey != "hifinicn",
                    requireArtistInTitle = false,
                ),
            )
        }
        for (strategy in strategies) {
            coroutineContext.ensureActive()
            if (isCancelled()) {
                ImportLog.i("站点取消: $siteKey strategy=${strategy.key}")
                return null
            }
            ImportLog.i(
                "策略尝试: $siteKey/${strategy.key} format=${strategy.searchKeywordFormat} " +
                    "requireQuality=${strategy.requireQualityTag} requireArtist=${strategy.requireArtistInTitle}",
            )
            val resolved = resolveWithStrategy(
                config,
                siteKey,
                site,
                strategy,
                artist,
                songName,
                excludedDetailUrls,
                onRejectedDetail,
            )
            if (resolved != null) return resolved
        }
        return null
    }

    fun searchDetails(
        config: ScrapeConfig,
        artist: String,
        songName: String,
    ): List<DetailCandidate> {
        val siteKey = config.preferredSource
        val site = config.sites[siteKey] ?: config.sites["hifinicn"] ?: return emptyList()
        val strategy = site.strategies.firstOrNull() ?: ScrapeStrategy(
            key = siteKey,
            searchPath = site.searchPath,
            searchKeywordFormat = "{artist}-{songName}",
            resultLinkSelector = site.resultLinkSelector,
        )
        return searchWithStrategy(config, site, strategy, artist, songName)
    }

    fun searchDetail(
        config: ScrapeConfig,
        artist: String,
        songName: String,
    ): DetailCandidate? = searchDetails(config, artist, songName).firstOrNull()

    fun resolvePlayUrl(
        config: ScrapeConfig,
        detailUrl: String,
        siteKey: String? = null,
    ): String? {
        val key = siteKey ?: config.preferredSource
        val site = config.sites[key] ?: config.sites.values.firstOrNull { detailUrl.startsWith(it.baseUrl) }
            ?: config.sites["hifinicn"]
            ?: return null
        val html = fetchHtml(detailUrl, site, config.userAgent)
        if (html == null) {
            ImportLog.w("详情页拉取失败: $key $detailUrl cookieLen=${site.cookie.length}")
            return null
        }
        val loginWall = html.contains("隐藏内容") && !html.contains("APlayer", ignoreCase = true)
        val raw = extractAudioUrl(html)
        val extracted = raw?.let { candidate ->
            val absolute = when {
                candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
                else -> resolveUrl(site.baseUrl, candidate)
            }
            normalizeAudioUrl(absolute)
        }
        if (extracted != null) {
            ImportLog.i("详情直链: $key raw=${raw?.take(80)} → ${extracted.take(100)}")
        } else {
            ImportLog.w(
                "详情无直链: $key loginWall=$loginWall hasAPlayer=${html.contains("APlayer", true)} " +
                    "hasGetmusic=${html.contains("getmusic.htm", true)} htmlLen=${html.length}",
            )
        }
        return extracted
    }

    fun audioUrlVariants(audioUrl: String): List<String> {
        val normalized = normalizeAudioUrl(audioUrl) ?: return emptyList()
        val variants = linkedSetOf(normalized)
        if (Regex("""\.mp3\.m4a($|\?)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) {
            variants += normalized.replace(Regex("""\.mp3\.m4a""", RegexOption.IGNORE_CASE), ".m4a")
            variants += normalized.replace(Regex("""\.mp3\.m4a""", RegexOption.IGNORE_CASE), ".mp3")
        }
        return variants.toList()
    }

    private fun resolveWithStrategy(
        config: ScrapeConfig,
        siteKey: String,
        site: ScrapeSite,
        strategy: ScrapeStrategy,
        artist: String,
        songName: String,
        excludedDetailUrls: Set<String> = emptySet(),
        onRejectedDetail: ((String) -> Unit)? = null,
    ): ResolvedPlayable? {
        val candidates = searchWithStrategy(config, site, strategy, artist, songName)
        ImportLog.i(
            "搜索结果: $siteKey/${strategy.key} 候选=${candidates.size} " +
                candidates.take(5).joinToString(" | ") { "${it.score}:${it.title}" },
        )
        val referer = site.audioDownloadReferer ?: (site.baseUrl.trimEnd('/') + "/")
        for ((idx, candidate) in candidates.take(5).withIndex()) {
            if (candidate.href in excludedDetailUrls) {
                ImportLog.i("跳过已拒绝详情[#${idx + 1}]: ${candidate.href}")
                continue
            }
            ImportLog.i(
                "尝试验证[#${idx + 1}] score=${candidate.score} title=${candidate.title} url=${candidate.href}",
            )
            val playUrl = resolvePlayUrl(config, candidate.href, siteKey)
            val playable = playUrl != null && isPlayableAudioUrl(playUrl, referer, site.cookie)
            if (!playable) {
                ImportLog.w(
                    "不可播[#${idx + 1}]: hasUrl=${playUrl != null} " +
                        "url=${playUrl?.take(80)} cookieLen=${site.cookie.length}",
                )
                onRejectedDetail?.invoke(candidate.href)
                continue
            }
            ImportLog.i("可播采纳[#${idx + 1}]: score=${candidate.score} play=${playUrl!!.take(100)}")
            return ResolvedPlayable(
                playUrl = playUrl,
                detailUrl = candidate.href,
                referer = referer,
                source = siteKey,
                matchScore = candidate.score,
                titleRaw = candidate.title,
            )
        }
        return null
    }

    /** 与后端 ensurePlayableAudioUrl 同思路：能拉到有效音频才算命中（getmusic.htm 需带 Cookie） */
    private fun isPlayableAudioUrl(playUrl: String, referer: String, cookie: String = ""): Boolean {
        for (candidate in audioUrlVariants(playUrl).ifEmpty { listOf(playUrl) }) {
            try {
                val builder = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", referer)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=0-2047")
                if (cookie.isNotBlank()) {
                    builder.header("Cookie", cookie)
                }
                client.newCall(builder.build()).execute().use { resp ->
                    val code = resp.code
                    if (code != 200 && code != 206) return@use
                    val bytes = resp.body?.bytes() ?: return@use
                    if (bytes.size < 256) return@use
                    val head = bytes.decodeToString(0, minOf(64, bytes.size))
                    if (head.contains("<html", ignoreCase = true) || head.contains("404")) return@use
                    if (head.startsWith("{") || head.startsWith("[")) return@use
                    val looksAudio = head.startsWith("ID3") ||
                        head.startsWith("fLaC") ||
                        head.startsWith("OggS") ||
                        head.startsWith("RIFF") ||
                        head.contains("ftyp") ||
                        (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0)
                    if (!looksAudio) return@use
                    return true
                }
            } catch (_: Exception) {
                // try next variant
            }
        }
        return false
    }

    private fun searchWithStrategy(
        config: ScrapeConfig,
        site: ScrapeSite,
        strategy: ScrapeStrategy,
        artist: String,
        songName: String,
    ): List<DetailCandidate> {
        val searchSong = songName.replace(Regex("""\(.*?\)|（.*?）"""), "").trim().ifBlank { songName }
        val format = strategy.searchKeywordFormat.ifBlank { "{songName}" }
        val keyword = format
            .replace("{artist}", artist.lowercase())
            .replace("{songName}", searchSong)
        val pathTemplate = strategy.searchPath ?: site.searchPath ?: return emptyList()
        val path = pathTemplate
            .replace("{keyword}", URLEncoder.encode(keyword, Charsets.UTF_8.name()))
            .replace("{page}", "1")
        val searchUrl = site.baseUrl.trimEnd('/') + if (path.startsWith("/")) path else "/$path"
        ImportLog.i("搜索请求: keyword=$keyword url=$searchUrl cookieLen=${site.cookie.length}")
        val html = fetchHtml(searchUrl, site, config.userAgent)
        if (html == null) {
            ImportLog.w("搜索页失败: $searchUrl")
            return emptyList()
        }
        return collectCandidates(
            html = html,
            baseUrl = site.baseUrl,
            artist = artist,
            songName = songName,
            strategy = strategy,
        )
    }

    fun scoreTitle(
        title: String,
        artist: String,
        songName: String,
        requireArtistInTitle: Boolean = false,
    ): Int {
        val processedTitle = normalize(title)
        val processedSong = normalize(songName)
        val processedArtist = normalize(artist)
        if (!processedTitle.contains(processedSong) && !titleMatchesSong(processedTitle, processedSong)) {
            return -1
        }
        var score = 50
        if (processedArtist.isNotBlank()) {
            if (processedTitle.contains(processedArtist)) {
                score += 45
            } else {
                val tokens = processedArtist.split(Regex("\\s+")).filter { it.length > 1 }
                if (tokens.isNotEmpty()) {
                    val hits = tokens.count { processedTitle.contains(it) }
                    score += ((40.0 * hits) / tokens.size).toInt()
                    if (hits == 0) score -= 5
                } else if (requireArtistInTitle) {
                    score -= 10
                }
            }
        }
        if (title.contains('～') || title.contains('~') || title.contains("串烧") || title.contains("合辑")) {
            score -= 35
        }
        return score
    }

    private fun collectCandidates(
        html: String,
        baseUrl: String,
        artist: String,
        songName: String,
        strategy: ScrapeStrategy,
    ): List<DetailCandidate> {
        val list = mutableListOf<DetailCandidate>()
        val minScore = if (strategy.requireArtistInTitle) 55 else 40
        val requireQuality = strategy.requireQualityTag

        fun push(titleRaw: String, hrefRaw: String) {
            val title = stripTags(titleRaw)
            if (title.isBlank() || hrefRaw.isBlank()) return
            if (requireQuality && QUALITY_MARKERS.none { title.contains(it) }) return
            val score = scoreTitle(title, artist, songName, strategy.requireArtistInTitle)
            if (score < minScore) return
            list.add(DetailCandidate(title, resolveUrl(baseUrl, hrefRaw), score))
        }

        val selector = strategy.resultLinkSelector
        if (!selector.isNullOrBlank() && selector.contains("text-primary")) {
            val primary = Pattern.compile(
                """href="((?:https?://[^"]+)?/song/[^"]+)"\s+class="[^"]*\btext-primary\b[^"]*"[\s\S]*?\btitle="([^"]*)"""",
                Pattern.CASE_INSENSITIVE,
            )
            val matcher = primary.matcher(html)
            while (matcher.find()) {
                push(matcher.group(2) ?: "", matcher.group(1) ?: "")
            }
        } else {
            val thread = Pattern.compile(
                """<li[^>]*class="[^"]*\bthread\b[^"]*"[^>]*>[\s\S]*?<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
                Pattern.CASE_INSENSITIVE,
            )
            val m = thread.matcher(html)
            while (m.find()) {
                push(m.group(2) ?: "", m.group(1) ?: "")
            }
            if (list.isEmpty()) {
                val subject = Pattern.compile(
                    """class="[^"]*\bsubject\b[^"]*"[^>]*>[\s\S]*?<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
                    Pattern.CASE_INSENSITIVE,
                )
                val sm = subject.matcher(html)
                while (sm.find()) {
                    push(sm.group(2) ?: "", sm.group(1) ?: "")
                }
            }
        }

        if (list.isEmpty()) {
            val loose = Pattern.compile(
                """href="((?:https?://[^"]+)?/(?:song/|thread-)[^"]+)"[\s\S]*?\btitle="([^"]*)"""",
                Pattern.CASE_INSENSITIVE,
            )
            val m = loose.matcher(html)
            while (m.find()) {
                push(m.group(2) ?: "", m.group(1) ?: "")
            }
        }
        return list.distinctBy { it.href }.sortedByDescending { it.score }.also { ranked ->
            if (ranked.isNotEmpty()) {
                ImportLog.i(
                    "打分排序: top=${ranked.first().score}:${ranked.first().title} " +
                        "共${ranked.size}条 minScore=$minScore",
                )
            }
        }
    }

    private fun extractAudioUrl(html: String): String? {
        val audioSrc = Pattern.compile(
            """<audio[^>]+src=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        if (audioSrc.find()) {
            return normalizeAudioUrl(audioSrc.group(1))
        }
        // 对齐服务端：APlayer 的 url 可能是 getmusic.htm?key=…（无后缀），不能要求扩展名
        val aplayerBlock = Pattern.compile(
            """APlayer[\s\S]{0,4000}?url\s*:\s*['"]([^'"]+)['"]""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        if (aplayerBlock.find()) {
            return normalizeAudioUrl(aplayerBlock.group(1))
        }
        val aplayerLoose = Pattern.compile(
            """url\s*:\s*['"]((?:https?:)?[^'"]*getmusic\.htm[^'"]*)['"]""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        if (aplayerLoose.find()) {
            return normalizeAudioUrl(aplayerLoose.group(1))
        }
        val aplayerExt = Pattern.compile(
            """url\s*:\s*['"](https?://[^'"]+\.(?:mp3|m4a|flac|wav|aac)[^'"]*)['"]""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        if (aplayerExt.find()) {
            return normalizeAudioUrl(aplayerExt.group(1))
        }
        val any = Pattern.compile(
            """(https?://[^"'\\\s>]+\.(?:mp3|m4a|flac|wav|aac)(?:\?[^"'\\\s>]*)?)""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        if (any.find()) {
            return normalizeAudioUrl(any.group(1))
        }
        return null
    }

    private fun fetchHtml(url: String, site: ScrapeSite, userAgent: String): String? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent.ifBlank { DEFAULT_UA })
            .header("Referer", site.baseUrl.trimEnd('/') + "/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (site.cookie.isNotBlank()) {
            builder.header("Cookie", site.cookie)
        }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val base = baseUrl.trimEnd('/')
        return if (href.startsWith("/")) "$base$href" else "$base/$href"
    }

    private fun stripTags(text: String?): String =
        text.orEmpty().replace(Regex("<[^>]+>"), "").trim()

    private fun normalize(text: String): String =
        text.trim().lowercase()
            .replace(Regex("[《》\\[\\]()（）]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun titleMatchesSong(title: String, song: String): Boolean {
        if (song.isBlank()) return false
        if (title.contains(song)) return true
        val tokens = song.split(Regex("\\s+")).filter { it.length > 1 }
        return tokens.isNotEmpty() && tokens.all { title.contains(it) }
    }

    private fun normalizeAudioUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var trimmed = raw.trim()
            .replace("&amp;", "&")
            .replace("\\/", "/")
        if (Regex("""\.{3,}""").containsMatchIn(trimmed) || trimmed.contains('…')) {
            return null
        }
        if (Regex("%[0-9A-Fa-f]{2}").containsMatchIn(trimmed)) {
            try {
                trimmed = java.net.URLDecoder.decode(
                    trimmed.replace("+", "%2B"),
                    Charsets.UTF_8.name(),
                )
            } catch (_: Exception) {
            }
        }
        return try {
            val u = java.net.URL(trimmed)
            java.net.URI(u.protocol, u.authority, u.path, u.query, u.ref).toASCIIString()
        } catch (_: Exception) {
            trimmed
        }
    }

    companion object {
        const val MIN_RELIABLE_SCORE = 95
        private val FALLBACK_SITES = listOf("hifiti", "hifini", "hifiii")
        private val QUALITY_MARKERS = listOf(
            "FLAC/MP3-320K/LRC歌词",
            "FLAC/MP3-320K",
            "MP3-320K",
            "MP3-128K",
            "FLAC",
        )
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

        /** 任一站满分（含 hifinicn）可早停 */
        fun isPerfectHit(resolved: ResolvedPlayable): Boolean =
            resolved.matchScore >= MIN_RELIABLE_SCORE
    }
}
