package com.fox.music.core.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fox.music.core.common.result.Result as AppResult
import com.fox.music.core.data.importqueue.ImportLog
import com.fox.music.core.data.importqueue.PlaylistImportScheduler
import com.fox.music.core.data.scrape.AudioDurationProbe
import com.fox.music.core.data.scrape.HifinicnScraper
import com.fox.music.core.database.dao.ImportJobDao
import com.fox.music.core.database.entity.ImportTrackJobEntity
import com.fox.music.core.database.entity.mergeRejectedLinks
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.UploadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@HiltWorker
class PlaylistImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importJobDao: ImportJobDao,
    private val importRepository: ImportRepository,
    private val playlistRepository: PlaylistRepository,
    private val uploadRepository: UploadRepository,
    private val scraper: HifinicnScraper,
    private val scheduler: PlaylistImportScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        ImportLog.i("Worker 启动 uniqueWork=${UNIQUE_WORK_NAME}")
        setForeground(createForegroundInfo("准备导入…", 0, 0))
        var processed = 0
        while (processed < MAX_TRACKS_PER_RUN) {
            val track = importJobDao.claimNextRunnableTrack() ?: break
            val job = importJobDao.getPlaylistJob(track.jobId)
            if (job == null || job.status == "paused") {
                if (job?.status == "paused") {
                    ImportLog.i("歌单任务已暂停 jobId=${track.jobId}，曲目改回 paused: ${track.title}")
                    importJobDao.updateTrack(track.trackJobId, status = "paused")
                }
                break
            }

            importJobDao.updatePlaylistJob(job.jobId, "running", job.done, job.failed)
            ImportLog.i(
                "开始处理曲目 [${job.done + job.failed + 1}/${job.total}] " +
                    "歌单「${job.playlistTitle}」→ ${track.artistNames} - ${track.title} " +
                    "trackJobId=${track.trackJobId}",
            )
            setForeground(
                createForegroundInfo(
                    text = "${job.playlistTitle}: ${track.title}",
                    done = (job.done + job.failed + 1).coerceAtMost(job.total),
                    total = job.total,
                    playlistId = track.playlistId,
                ),
            )

            try {
                processTrack(track)
                ImportLog.i("曲目处理成功: ${track.artistNames} - ${track.title}")
            } catch (e: Exception) {
                ImportLog.e("曲目处理失败: ${track.artistNames} - ${track.title}", e)
                val latest = importJobDao.getTrack(track.trackJobId) ?: track
                importJobDao.updateTrack(
                    trackJobId = track.trackJobId,
                    status = "failed",
                    error = e.message ?: "导入失败",
                    rejectedLinksJson = latest.rejectedLinksJson,
                )
            }

            val done = importJobDao.countCompleted(track.jobId)
            val failed = importJobDao.countFailed(track.jobId)
            val remaining = importJobDao.countRemaining(track.jobId)
            val status = jobStatus(remaining, failed)
            importJobDao.updatePlaylistJob(track.jobId, status, done, failed)
            ImportLog.i(
                "歌单进度 jobId=${track.jobId} status=$status done=$done failed=$failed remaining=$remaining",
            )
            when (status) {
                "completed" -> {
                    ImportLog.i("导入全部成功，清理任务数据 jobId=${track.jobId}")
                    importJobDao.deleteJobCompletely(track.jobId)
                }
                "needs_retry" -> {
                    ImportLog.i("导入结束有失败，收缩为失败批次 jobId=${track.jobId} failed=$failed")
                    importJobDao.shrinkToFailedOnly(track.jobId)
                }
            }
            importRepository.updateImportSession(
                playlistId = track.playlistId,
                totalTracks = job.total,
                importedTracks = done,
                status = if (remaining == 0) "completed" else "in_progress",
            )
            processed++
        }

        val next = importJobDao.getNextQueuedTrack()
        if (next != null) {
            ImportLog.i("本轮结束，仍有排队曲目，续跑 Worker")
            scheduler.kickWorker(forceNew = true)
        } else {
            ImportLog.i("本轮结束，队列为空 processed=$processed")
        }
        androidx.work.ListenableWorker.Result.success()
    }

    private suspend fun processTrack(track: ImportTrackJobEntity) {
        val firstArtist = track.artistNames.split(",").firstOrNull()?.trim().orEmpty()
        val rejected = track.rejectedLinks().toMutableSet()
        // 歌单平台官方时长（毫秒）：有值即作为「版本正确性」交叉校验基准
        val expectedDurationMs = track.durationMs.takeIf { it > 0 }
        ImportLog.i(
            "①曲库查找: 「$firstArtist - ${track.title}」已拒绝详情数=${rejected.size} " +
                "原曲时长=${expectedDurationMs?.let { "${it}ms" } ?: "未知"}",
        )

        when (val found = importRepository.findMusic(track.title, firstArtist)) {
            is AppResult.Success -> {
                val musicId = found.data
                if (musicId != null) {
                    ImportLog.i("①命中曲库，复用 musicId=$musicId，跳过解析下载")
                    playlistRepository.addTracks(track.playlistId, listOf(musicId))
                    importJobDao.updateTrack(track.trackJobId, status = "completed", musicId = musicId)
                    return
                }
                ImportLog.i("①曲库未命中，继续解析")
            }
            is AppResult.Error -> ImportLog.w("①曲库查找出错: ${found.message}，继续解析")
            else -> {}
        }

        val config = when (val cfg = importRepository.getScrapeConfig()) {
            is AppResult.Success -> cfg.data.also {
                ImportLog.i(
                    "②拉取爬虫配置: preferred=${it.preferredSource} " +
                        "站点=${it.sites.keys.joinToString()} " +
                        "Cookie长度=${it.sites.entries.joinToString { (k, v) -> "$k:${v.cookie.length}" }}",
                )
            }
            is AppResult.Error -> throw IllegalStateException(cfg.message ?: "无法获取爬虫配置")
            else -> throw IllegalStateException("无法获取爬虫配置")
        }

        var detailUrl = track.detailUrl
        var matchScore = track.matchScore
        var titleRaw = track.titleRaw
        var fromAsset = !detailUrl.isNullOrBlank()
        var sourceKey = config.preferredSource

        if (!detailUrl.isNullOrBlank() && detailUrl in rejected) {
            ImportLog.w("③本地缓存详情在拒绝列表中，丢弃: $detailUrl")
            detailUrl = null
            fromAsset = false
        }

        if (detailUrl.isNullOrBlank()) {
            when (val lookup = importRepository.lookupSourceLink(track.title, firstArtist)) {
                is AppResult.Success -> {
                    val link = lookup.data
                    if (link != null && link.detailUrl !in rejected) {
                        detailUrl = link.detailUrl
                        matchScore = link.matchScore
                        titleRaw = link.titleRaw
                        fromAsset = true
                        ImportLog.i(
                            "③命中已验证音源资产 score=$matchScore titleRaw=$titleRaw url=$detailUrl",
                        )
                    } else {
                        ImportLog.i("③无可用音源资产")
                    }
                }
                is AppResult.Error -> ImportLog.w("③音源资产查找失败: ${lookup.message}")
                else -> {}
            }
        } else {
            ImportLog.i("③使用任务已有详情页: $detailUrl score=$matchScore")
        }

        var playUrl: String? = null
        var referer = config.sites[config.preferredSource]?.audioDownloadReferer
            ?: "https://hifinicn.com/"
        var audioLocal: File? = null
        var audioRemote: String? = null

        if (!detailUrl.isNullOrBlank()) {
            ImportLog.i("④解析资产详情直链: $detailUrl source=$sourceKey")
            val resolved = scraper.resolvePlayUrl(config, detailUrl!!, sourceKey)
            if (resolved != null) {
                playUrl = resolved
                ImportLog.i("④直链提取成功: ${resolved.take(120)}")
                val cookie = config.sites[sourceKey]?.cookie.orEmpty()
                ImportLog.i("⑤下载音频(资产) cookieLen=${cookie.length} referer=$referer")
                audioLocal = downloadWithReferer(resolved, referer, cookie)
                ImportLog.i("⑤本地下载${if (audioLocal != null) "成功 size=${audioLocal.length()}" else "失败"}")
                if (audioLocal != null && expectedDurationMs != null) {
                    val actualMs = AudioDurationProbe.probeMs(audioLocal)
                    if (!AudioDurationProbe.isClose(actualMs, expectedDurationMs)) {
                        // 资产是人工/自动验证过的详情页，可信度高于网页搜索：
                        // 时长偏差只告警不拦（现场版/剪辑版常见），拦了会白丢已验证资产
                        ImportLog.w(
                            "⑤资产时长偏差 actual=${actualMs}ms expected=${expectedDurationMs}ms（已入库资产，仍采纳）",
                        )
                    } else {
                        ImportLog.i("⑤资产时长校验通过 actual=${actualMs}ms expected=${expectedDurationMs}ms")
                    }
                }
                audioRemote = ingestAudio(audioLocal, playUrl!!, referer)
                ImportLog.i("⑥上传/入库音频${if (audioRemote != null) "成功: ${audioRemote.take(80)}" else "失败"}")
            } else {
                ImportLog.w("④资产详情未能提取直链")
            }
            if (audioRemote == null) {
                ImportLog.w("④资产链路失败，标记拒绝并改走多站解析")
                rememberRejected(track, rejected, listOfNotNull(detailUrl, playUrl), firstArtist, sourceKey, titleRaw)
                detailUrl = null
                fromAsset = false
            }
        }

        if (audioRemote == null) {
            ImportLog.i("⑦客户端多站解析开始: $firstArtist - ${track.title} 排除=${rejected.size}")
            val clientResolved = scraper.resolveForImport(
                config = config,
                artist = firstArtist,
                songName = track.title,
                excludedDetailUrls = rejected,
                onRejectedDetail = { url -> rejected.add(url) },
            )
            if (clientResolved != null) {
                detailUrl = clientResolved.detailUrl
                matchScore = clientResolved.matchScore
                titleRaw = clientResolved.titleRaw
                sourceKey = clientResolved.source
                referer = clientResolved.referer ?: referer
                playUrl = clientResolved.playUrl
                fromAsset = false
                ImportLog.i(
                    "⑦多站评选采纳: source=$sourceKey score=$matchScore " +
                        "titleRaw=$titleRaw detail=$detailUrl play=${playUrl?.take(100)}",
                )
                audioLocal = downloadWithReferer(
                    clientResolved.playUrl,
                    referer,
                    config.sites[sourceKey]?.cookie.orEmpty(),
                )
                ImportLog.i("⑦.1 本地下载${if (audioLocal != null) "成功 size=${audioLocal.length()}" else "失败"}")

                // 时长闸门：网页标题匹配最易出错版本，实测为试听片段/截断的直接弃用
                if (audioLocal != null) {
                    val actualMs = AudioDurationProbe.probeMs(audioLocal)
                    if (!AudioDurationProbe.acceptable(actualMs, expectedDurationMs)) {
                        ImportLog.w(
                            "⑦.2 时长不足（疑似试听/截断），弃用该站候选: source=$sourceKey " +
                                "actual=${actualMs}ms expected=${expectedDurationMs}ms title=$titleRaw",
                        )
                        rememberRejected(
                            track,
                            rejected,
                            listOfNotNull(detailUrl, playUrl),
                            firstArtist,
                            sourceKey,
                            titleRaw,
                        )
                        audioLocal.delete()
                        audioLocal = null
                        // 重要：清空详情页，避免错误版本被 ⑯ 当作可信资产上报
                        detailUrl = null
                        matchScore = 0
                        titleRaw = null
                    } else if (!AudioDurationProbe.isClose(actualMs, expectedDurationMs)) {
                        ImportLog.w(
                            "⑦.2 时长偏差较大但为完整音频，仍采纳: actual=${actualMs}ms " +
                                "expected=${expectedDurationMs}ms（可能是不同版本/母带）",
                        )
                    } else {
                        ImportLog.i("⑦.2 时长校验通过 actual=${actualMs}ms expected=${expectedDurationMs}ms")
                    }
                }

                if (audioLocal != null) {
                    importJobDao.updateTrack(
                        trackJobId = track.trackJobId,
                        status = "running",
                        detailUrl = detailUrl,
                        matchScore = matchScore,
                        titleRaw = titleRaw,
                        rejectedLinksJson = mergeRejectedLinks(null, rejected),
                    )
                    audioRemote = ingestAudio(audioLocal, clientResolved.playUrl, referer)
                    ImportLog.i("⑦.3 上传/入库音频${if (audioRemote != null) "成功: ${audioRemote.take(80)}" else "失败"}")
                    if (audioRemote == null) {
                        rememberRejected(
                            track,
                            rejected,
                            listOfNotNull(detailUrl, playUrl),
                            firstArtist,
                            sourceKey,
                            titleRaw,
                        )
                    }
                }
            } else {
                ImportLog.w("⑦多站解析无可用结果")
                if (rejected.isNotEmpty()) {
                    persistRejected(track, rejected)
                }
            }
        }

        // ⑦.5 服务端 API 多源候选（2026-10-21 新增）
        // 音源纠错同款链路：网易云/酷我/Hifini解析/插件源。这些源自带官方时长
        // 与艺人，比网页标题匹配准得多；一次返回多条候选，逐条试下载，
        // 单源失效（死链/30s 试听/版本不符）不影响其它候选。
        if (audioRemote == null) {
            ImportLog.i("⑦.5 服务端API多源候选: $firstArtist - ${track.title} expected=${expectedDurationMs}ms")
            when (
                val resp = importRepository.searchAudioCandidates(
                    title = track.title,
                    artist = firstArtist,
                    durationMs = expectedDurationMs,
                )
            ) {
                is AppResult.Success -> {
                    val list = resp.data
                    ImportLog.i(
                        "⑦.5 候选数=${list.size} " +
                            list.take(6).joinToString(" | ") {
                                "${it.source}:${it.score}${if (it.durationMatch) "" else "(时长不符)"}"
                            },
                    )
                    for ((idx, candidate) in list.withIndex()) {
                        val tag = "[#${idx + 1}/${list.size}] source=${candidate.source} " +
                            "score=${candidate.score} exact=${candidate.exact} " +
                            "durationMatch=${candidate.durationMatch} title=${candidate.title}"
                        if (candidate.url in rejected) {
                            ImportLog.i("⑦.5 跳过已拒绝候选 $tag")
                            continue
                        }
                        val candidateReferer = candidate.referer ?: referer
                        ImportLog.i("⑦.5 试下载 $tag")
                        // API 源不需要音源站 Cookie（带上去反而可能干扰 CDN 校验）
                        val local = downloadWithReferer(candidate.url, candidateReferer, cookie = "")
                        if (local == null) {
                            ImportLog.w("⑦.5 下载失败 $tag")
                            continue
                        }
                        val actualMs = AudioDurationProbe.probeMs(local)
                        if (!AudioDurationProbe.acceptable(actualMs, expectedDurationMs)) {
                            ImportLog.w(
                                "⑦.5 时长不足（疑似试听/截断），换下一条 $tag " +
                                    "actual=${actualMs}ms expected=${expectedDurationMs}ms",
                            )
                            local.delete()
                            continue
                        }
                        if (!AudioDurationProbe.isClose(actualMs, expectedDurationMs)) {
                            ImportLog.w(
                                "⑦.5 时长偏差较大但为完整音频，采纳 $tag " +
                                    "actual=${actualMs}ms expected=${expectedDurationMs}ms",
                            )
                        }
                        val uploaded = ingestAudio(local, candidate.url, candidateReferer)
                        local.delete()
                        if (uploaded == null) {
                            ImportLog.w("⑦.5 上传失败 $tag")
                            continue
                        }
                        audioRemote = uploaded
                        playUrl = candidate.url
                        referer = candidateReferer
                        sourceKey = candidate.source ?: sourceKey
                        titleRaw = candidate.title.takeIf { it.isNotBlank() }
                        // 服务端 API 源的 detailUrl 是平台歌曲页（非 hifini 详情页），
                        // 不参与 ⑯ 的 hifini 资产上报（服务端 resolver 已自行沉淀资产）
                        detailUrl = null
                        ImportLog.i(
                            "⑦.5 采纳 $tag actual=${actualMs}ms audio=${uploaded.take(80)}",
                        )
                        break
                    }
                    if (audioRemote == null) ImportLog.w("⑦.5 服务端候选全部不可用")
                }
                is AppResult.Error -> ImportLog.w("⑦.5 服务端候选查询失败: ${resp.message}")
                else -> {}
            }
        }

        if (audioRemote == null) {
            ImportLog.i("⑩服务端 resolve-audio 兜底: $firstArtist - ${track.title}")
            when (val server = importRepository.resolveAudio(track.title, firstArtist, expectedDurationMs)) {
                is AppResult.Success -> {
                    val r = server.data
                    playUrl = r.url
                    referer = r.referer ?: referer
                    sourceKey = r.source ?: sourceKey
                    ImportLog.i(
                        "⑩服务端返回 source=${r.source} score=${r.score} exact=${r.exact} url=${r.url.take(100)}",
                    )
                    // 服务端已用 API 源做过时长判定，这里再实测一次兜住 30s 试听片段
                    audioLocal = downloadWithReferer(
                        r.url,
                        referer,
                        config.sites[sourceKey]?.cookie.orEmpty(),
                    )
                    if (audioLocal != null) {
                        val actualMs = AudioDurationProbe.probeMs(audioLocal)
                        if (!AudioDurationProbe.acceptable(actualMs, expectedDurationMs)) {
                            ImportLog.w(
                                "⑩服务端音源时长不足（疑似试听/截断），弃用: actual=${actualMs}ms " +
                                    "expected=${expectedDurationMs}ms",
                            )
                            audioLocal.delete()
                            audioLocal = null
                        }
                    }
                    if (audioLocal != null) {
                        audioRemote = ingestAudio(audioLocal, r.url, referer)
                    }
                    ImportLog.i("⑩服务端音源上传${if (audioRemote != null) "成功" else "失败"}")
                    if (audioRemote == null) {
                        rememberRejected(track, rejected, listOf(r.url), firstArtist, sourceKey, titleRaw)
                    }
                }
                is AppResult.Error -> ImportLog.w("⑩服务端兜底失败: ${server.message}")
                else -> {}
            }
        }

        val finalRemote = audioRemote
            ?: throw IllegalStateException("多站逆向、服务端API候选与服务端兜底均未拿到可用音频")
        audioLocal?.delete()
        ImportLog.i("⑪最终音频 URL: ${finalRemote.take(120)}")

        var coverRemote: String? = null
        val coverCandidates = listOfNotNull(track.coverUrl, track.albumCover)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        for (cover in coverCandidates) {
            coverRemote = when (val up = uploadRepository.uploadImageByUrl(cover)) {
                is AppResult.Success -> up.data
                else -> null
            }
            if (!coverRemote.isNullOrBlank()) break
        }
        ImportLog.i("⑫封面上传${if (coverRemote != null) "成功" else "跳过/失败"}")

        var albumCoverRemote: String? = coverRemote
        if (albumCoverRemote.isNullOrBlank() && !track.albumCover.isNullOrBlank()) {
            albumCoverRemote = when (val up = uploadRepository.uploadImageByUrl(track.albumCover!!)) {
                is AppResult.Success -> up.data
                else -> null
            }
        }

        var lyrics = track.lyrics.orEmpty()
        var lyricsTrans = ""
        val platform = track.platform
        val externalId = track.externalId
        if (lyrics.isNotBlank()) {
            // 入队时已预取落库，直接用，省一次网络
            ImportLog.i("⑬歌词取自本地缓存 lyricLen=${lyrics.length}")
        } else if (!platform.isNullOrBlank() && !externalId.isNullOrBlank()) {
            when (val lrc = importRepository.fetchLyrics(platform, externalId)) {
                is AppResult.Success -> {
                    lyrics = lrc.data.lyric
                    lyricsTrans = lrc.data.lyricTrans
                    if (!lyrics.isNullOrBlank()) {
                        // 补进任务表，详情页的手动换源也能拿到
                        runCatching { importJobDao.fillTrackLyrics(track.trackJobId, lyrics) }
                    }
                    ImportLog.i("⑬歌词拉取成功 lyricLen=${lyrics.length} transLen=${lyricsTrans.length}")
                }
                else -> ImportLog.w("⑬歌词拉取失败 platform=$platform id=$externalId")
            }
        } else {
            ImportLog.i("⑬无 platform/externalId，跳过歌词")
        }

        val artists = track.artistNames.split(",").map { it.trim() }.filter { it.isNotBlank() }
        ImportLog.i("⑭find-or-create 歌曲: $artists - ${track.title}")
        val musicId = when (
            val created = importRepository.findOrCreateMusic(
                title = track.title,
                url = finalRemote,
                coverImage = coverRemote,
                durationMs = track.durationMs,
                artists = artists,
                lyrics = lyrics,
                lyricsTrans = lyricsTrans,
                albumName = track.albumName,
                albumCover = albumCoverRemote,
                trackNumber = track.trackIndex + 1,
                platform = platform,
                externalId = externalId,
            )
        ) {
            is AppResult.Success -> created.data.also {
                ImportLog.i("⑭创建/复用歌曲成功 musicId=$it")
            }
            is AppResult.Error -> throw IllegalStateException(created.message ?: "创建歌曲失败")
            else -> throw IllegalStateException("创建歌曲失败")
        }

        playlistRepository.addTracks(track.playlistId, listOf(musicId))
        ImportLog.i("⑮绑定歌单 playlistId=${track.playlistId} musicId=$musicId")

        if (!fromAsset && !detailUrl.isNullOrBlank() && matchScore >= HifinicnScraper.MIN_RELIABLE_SCORE) {
            ImportLog.i("⑯上报可靠音源资产 source=$sourceKey score=$matchScore url=$detailUrl")
            importRepository.upsertSourceLink(
                title = track.title,
                artist = firstArtist,
                detailUrl = detailUrl!!,
                matchScore = matchScore,
                titleRaw = titleRaw,
                source = sourceKey,
                origin = "playlist-import",
            )
        } else {
            ImportLog.i(
                "⑯跳过资产上报 fromAsset=$fromAsset score=$matchScore detailEmpty=${detailUrl.isNullOrBlank()}",
            )
        }

        importJobDao.updateTrack(
            trackJobId = track.trackJobId,
            status = "completed",
            detailUrl = detailUrl,
            matchScore = matchScore,
            titleRaw = titleRaw,
            musicId = musicId,
            rejectedLinksJson = mergeRejectedLinks(track.rejectedLinksJson, rejected),
        )
    }

    private suspend fun rememberRejected(
        track: ImportTrackJobEntity,
        rejected: MutableSet<String>,
        urls: Collection<String>,
        artist: String,
        source: String,
        titleRaw: String?,
    ) {
        urls.map { it.trim() }.filter { it.isNotBlank() }.forEach { rejected.add(it) }
        persistRejected(track, rejected)
        urls.filter { it.startsWith("http") && looksLikeDetailPage(it) }.forEach { url ->
            importRepository.reportUnreliableLink(
                title = track.title,
                artist = artist,
                detailUrl = url,
                titleRaw = titleRaw,
                source = source,
            )
        }
    }

    private suspend fun persistRejected(track: ImportTrackJobEntity, rejected: Set<String>) {
        importJobDao.updateTrack(
            trackJobId = track.trackJobId,
            status = "running",
            rejectedLinksJson = mergeRejectedLinks(track.rejectedLinksJson, rejected),
        )
    }

    private suspend fun ingestAudio(
        audioLocal: File?,
        playUrl: String,
        referer: String,
    ): String? {
        if (audioLocal != null && looksLikeAudio(audioLocal)) {
            when (val up = uploadRepository.uploadAudioFile(audioLocal, audioLocal.name)) {
                is AppResult.Success -> return up.data
                else -> {}
            }
        }
        return when (val up2 = uploadRepository.uploadAudioByUrl(playUrl, referer)) {
            is AppResult.Success -> up2.data
            else -> null
        }
    }

    private fun downloadWithReferer(url: String, referer: String, cookie: String = ""): File? {
        val client = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .build()
        for (candidate in scraper.audioUrlVariants(url).ifEmpty { listOf(url) }) {
            try {
                val builder = okhttp3.Request.Builder()
                    .url(candidate)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
                    )
                    .header("Referer", referer)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                if (cookie.isNotBlank()) {
                    builder.header("Cookie", cookie)
                }
                client.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    val dir = File(applicationContext.cacheDir, "import_audio")
                    if (!dir.exists()) dir.mkdirs()
                    val ext = audioExtension(candidate, resp.header("Content-Type"))
                    val file = File(dir, "audio_${UUID.randomUUID()}$ext")
                    file.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (!looksLikeAudio(file)) {
                        file.delete()
                        return@use
                    }
                    return file
                }
            } catch (_: Exception) {
                // try next variant
            }
        }
        return null
    }

    private fun createForegroundInfo(
        text: String,
        done: Int,
        total: Int,
        playlistId: Long = 0L,
    ): ForegroundInfo {
        ensureChannel()
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("正在导入歌单")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0)
        if (playlistId > 0L) {
            builder.setContentIntent(playlistDetailPendingIntent(playlistId))
        }
        val notification: Notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun playlistDetailPendingIntent(playlistId: Long): PendingIntent {
        val route = "playlist_detail/$playlistId/PLAYLIST"
        val intent = Intent().apply {
            setClassName(applicationContext.packageName, MAIN_ACTIVITY_CLASS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_ROUTE, route)
        }
        val requestCode = (playlistId xor (playlistId ushr 32)).toInt().let {
            if (it == 0) NOTIFICATION_ID else it
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "歌单导入",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "playlist_import_queue"
        const val WORK_TAG = "playlist_import"
        private const val CHANNEL_ID = "fox_playlist_import_channel"
        private const val NOTIFICATION_ID = 41021
        private const val MAX_TRACKS_PER_RUN = 5
        private const val MAIN_ACTIVITY_CLASS = "com.fox.music.MainActivity"
        /** 与 FoxNotificationManager.EXTRA_START_ROUTE / MainActivity 一致 */
        private const val EXTRA_START_ROUTE = "start_route"

        private fun looksLikeDetailPage(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".flac") ||
                lower.contains(".wav") || lower.contains("/mp3/")
            ) {
                return false
            }
            return true
        }

        fun jobStatus(remaining: Int, failed: Int): String = when {
            remaining > 0 -> "running"
            failed > 0 -> "needs_retry"
            else -> "completed"
        }

        private fun audioExtension(url: String, contentType: String?): String {
            val type = contentType.orEmpty().lowercase()
            val path = url.substringBefore('?').lowercase()
            return when {
                type.contains("mpeg") || type.contains("mp3") || path.endsWith(".mp3") -> ".mp3"
                type.contains("flac") || path.endsWith(".flac") -> ".flac"
                type.contains("wav") || path.endsWith(".wav") -> ".wav"
                type.contains("ogg") || path.endsWith(".ogg") -> ".ogg"
                else -> ".m4a"
            }
        }

        fun looksLikeAudio(file: File): Boolean {
            if (!file.exists() || file.length() < 1024) return false
            val head = ByteArray(16)
            val n = file.inputStream().use { it.read(head) }
            if (n < 4) return false
            val ascii = String(head, 0, n, Charsets.ISO_8859_1)
            if (ascii.contains("<html", ignoreCase = true) || ascii.startsWith("{") || ascii.startsWith("[")) {
                return false
            }
            return ascii.startsWith("ID3") ||
                ascii.startsWith("fLaC") ||
                ascii.startsWith("OggS") ||
                ascii.startsWith("RIFF") ||
                ascii.contains("ftyp") ||
                (head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0)
        }
    }
}
