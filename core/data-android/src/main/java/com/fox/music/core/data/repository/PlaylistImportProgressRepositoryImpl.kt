package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.data.importqueue.ImportLog
import com.fox.music.core.data.importqueue.PlaylistImportScheduler
import com.fox.music.core.database.dao.ImportJobDao
import com.fox.music.core.database.entity.ImportTrackJobEntity
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.ImportTrackItem
import com.fox.music.core.domain.repository.PlaylistImportDetail
import com.fox.music.core.domain.repository.PlaylistImportProgress
import com.fox.music.core.domain.repository.PlaylistImportProgressRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.UploadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaylistImportProgressRepositoryImpl @Inject constructor(
    private val importJobDao: ImportJobDao,
    private val scheduler: PlaylistImportScheduler,
    private val importRepository: ImportRepository,
    private val playlistRepository: PlaylistRepository,
    private val uploadRepository: UploadRepository,
) : PlaylistImportProgressRepository {

    override fun observe(playlistId: Long): Flow<PlaylistImportProgress?> =
        combine(
            importJobDao.observeLatestJobForPlaylist(playlistId),
            importJobDao.observeRunningTrackForPlaylist(playlistId),
        ) { job, running ->
            job?.takeIf { it.status != "completed" }?.let {
                PlaylistImportProgress(
                    jobId = it.jobId,
                    playlistId = it.playlistId,
                    status = it.status,
                    total = it.total,
                    done = it.done,
                    failed = it.failed,
                    currentTitle = running?.title,
                )
            }
        }

    override fun observeDetail(playlistId: Long): Flow<PlaylistImportDetail?> =
        combine(
            importJobDao.observeLatestJobForPlaylist(playlistId),
            importJobDao.observeTracksForPlaylist(playlistId),
        ) { job, tracks ->
            if (job == null) return@combine null
            PlaylistImportDetail(
                jobId = job.jobId,
                playlistId = job.playlistId,
                playlistTitle = job.playlistTitle,
                status = job.status,
                total = job.total,
                done = job.done,
                failed = job.failed,
                // 只展示当前任务的曲目：重试批次会重建记录，旧批次残留不混进来；
                // completed 不再占行（手动换源成功后应当立刻从失败列表消失）
                tracks = tracks
                    .filter { it.jobId == job.jobId && it.status != "completed" }
                    .map { it.toUiItem() },
            )
        }

    override suspend fun pause(jobId: String) {
        scheduler.pause(jobId)
    }

    override suspend fun resume(jobId: String) {
        scheduler.resume(jobId)
    }

    override suspend fun retryFailed(jobId: String) {
        scheduler.retryFailed(jobId)
    }

    override suspend fun cancelFailed(jobId: String) {
        ImportLog.i("取消失败导入数据 jobId=$jobId")
        importJobDao.cancelFailed(jobId)
    }

    override suspend fun cacheTrackLyrics(trackJobId: String, lyrics: String) {
        if (lyrics.isBlank()) return
        val rows = importJobDao.fillTrackLyrics(trackJobId, lyrics)
        ImportLog.i("缓存原曲歌词 trackJobId=$trackJobId len=${lyrics.length} rows=$rows")
    }

    override suspend fun retryTrack(trackJobId: String) {        val track = importJobDao.getTrack(trackJobId) ?: return
        val job = importJobDao.getPlaylistJob(track.jobId)
        if (job?.status == "paused") {
            // paused 任务下的排队曲目不会被 Worker 领取，直接忽略避免「点了没反应」
            ImportLog.w("任务已暂停，忽略单曲重试 trackJobId=$trackJobId")
            return
        }
        ImportLog.i("单曲重试 trackJobId=$trackJobId ${track.artists()} - ${track.title}")
        importJobDao.requeueTrack(trackJobId)
        if (job != null) {
            importJobDao.updatePlaylistJob(job.jobId, "queued", job.done, job.failed)
        }
        scheduler.kickWorker()
    }

    override suspend fun applyManualSource(
        trackJobId: String,
        url: String,
        referer: String?,
        durationMs: Long?,
    ): Result<Long> {
        if (url.isBlank()) {
            return Result.Error(IllegalArgumentException("音源地址为空"), "音源地址为空")
        }
        val track = importJobDao.getTrack(trackJobId)
            ?: return Result.Error(IllegalStateException("导入记录已失效"), "导入记录已失效")
        ImportLog.i(
            "手动指定音源 trackJobId=$trackJobId ${track.artists()} - ${track.title} " +
                "url=${url.take(100)} referer=$referer",
        )

        // ① 转存音源：服务端下载后写 OSS，避免第三方直链过期/防盗链
        // 转存前先探活：实测 hifinicn 源会解析出 404 的 HTML 页面、部分源会给试听片段，
        // 直接转存会把「不是音频」的东西写成正式 music.url，先拦掉。
        if (!probeAudioUrl(url, referer)) {
            ImportLog.w("手动指定音源-探活失败（非音频/不可下载）url=${url.take(100)}")
            return Result.Error(
                IllegalStateException("该音源不可下载"),
                "该音源无法下载（返回的不是音频，换一个候选试试）",
            )
        }
        val remoteUrl = when (val up = uploadRepository.uploadAudioByUrl(url, referer)) {
            is Result.Success -> up.data
            is Result.Error -> {
                val msg = up.message ?: up.exception.message ?: "音频转存失败"
                ImportLog.e("手动指定音源-转存失败: $msg")
                return Result.Error(up.exception, msg)
            }
            Result.Loading -> return Result.Error(IllegalStateException("音频转存中"), "音频转存中")
        }
        if (remoteUrl.isBlank()) {
            return Result.Error(IllegalStateException("音频转存失败"), "音频转存失败")
        }
        ImportLog.i("手动指定音源-转存成功 ${remoteUrl.take(100)}")

        // ② 封面（尽力而为，失败不拦）
        val cover = track.coverUrl?.takeIf { it.isNotBlank() }?.let { coverUrl ->
            when (val up = uploadRepository.uploadImageByUrl(coverUrl)) {
                is Result.Success -> up.data
                else -> null
            }
        } ?: track.albumCover?.takeIf { it.isNotBlank() }?.let { coverUrl ->
            when (val up = uploadRepository.uploadImageByUrl(coverUrl)) {
                is Result.Success -> up.data
                else -> null
            }
        }

        // ③ 歌词（有平台 id 时才拉，失败不拦）
        var lyrics = ""
        var lyricsTrans = ""
        val platform = track.platform
        val externalId = track.externalId
        if (!platform.isNullOrBlank() && !externalId.isNullOrBlank()) {
            when (val lrc = importRepository.fetchLyrics(platform, externalId)) {
                is Result.Success -> {
                    lyrics = lrc.data.lyric
                    lyricsTrans = lrc.data.lyricTrans
                }
                else -> ImportLog.w("手动指定音源-歌词拉取失败 platform=$platform id=$externalId")
            }
        }

        // ④ 建/复用歌曲
        val finalDuration = maxOf(track.durationMs, durationMs ?: 0L)
        val musicId = when (
            val created = importRepository.findOrCreateMusic(
                title = track.title,
                url = remoteUrl,
                coverImage = cover,
                durationMs = finalDuration,
                artists = track.artistList(),
                lyrics = lyrics,
                lyricsTrans = lyricsTrans,
                albumName = track.albumName,
                albumCover = cover,
                trackNumber = track.trackIndex + 1,
                platform = platform,
                externalId = externalId,
            )
        ) {
            is Result.Success -> created.data
            is Result.Error -> {
                val msg = created.message ?: "创建歌曲失败"
                ImportLog.e("手动指定音源-建歌失败: $msg")
                return Result.Error(created.exception, msg)
            }
            Result.Loading -> return Result.Error(IllegalStateException("创建歌曲中"), "创建歌曲中")
        }
        ImportLog.i("手动指定音源-入库成功 musicId=$musicId")

        // ⑤ 加回歌单 + 标记该曲目已导入
        val added = playlistRepository.addTracks(track.playlistId, listOf(musicId))
        if (added is Result.Error) {
            val msg = added.message ?: "加入歌单失败"
            ImportLog.e("手动指定音源-加歌单失败: $msg")
            return Result.Error(added.exception, msg)
        }
        importJobDao.updateTrack(
            trackJobId = trackJobId,
            status = "completed",
            error = null,
            musicId = musicId,
        )

        // ⑥ 重算任务状态：失败清零则整批收尾（歌单详情页的导入横幅随之消失）
        val done = importJobDao.countCompleted(track.jobId)
        val failed = importJobDao.countFailed(track.jobId)
        val remaining = importJobDao.countRemaining(track.jobId)
        val job = importJobDao.getPlaylistJob(track.jobId)
        val status = when {
            remaining > 0 -> job?.status ?: "running"
            failed > 0 -> "needs_retry"
            else -> "completed"
        }
        importJobDao.updatePlaylistJob(track.jobId, status, done, failed)
        ImportLog.i(
            "手动指定音源-完成 status=$status done=$done failed=$failed remaining=$remaining",
        )
        return Result.Success(musicId)
    }

    /**
     * 音源探活：只取前 1KB，按文件头判断是不是真音频。
     * 拦掉 404/403 页面、HTML 详情页、JSON 错误体（这些转存后就是一条坏歌）。
     */
    private suspend fun probeAudioUrl(url: String, referer: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1023")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
                    )
                    .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                    .build()
                okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { resp ->
                        if (!resp.isSuccessful) return@withContext false
                        val body = resp.body ?: return@withContext false
                        val head = ByteArray(16)
                        val n = body.byteStream().read(head)
                        if (n < 4) return@withContext false
                        val ascii = String(head, 0, n, Charsets.ISO_8859_1)
                        val trimmed = ascii.trimStart()
                        if (ascii.contains("<html", ignoreCase = true) ||
                            ascii.contains("<!doctype", ignoreCase = true) ||
                            trimmed.startsWith("{") ||
                            trimmed.startsWith("<?xml")
                        ) {
                            return@withContext false
                        }
                        ascii.startsWith("ID3") ||
                            ascii.startsWith("fLaC") ||
                            ascii.startsWith("OggS") ||
                            ascii.startsWith("RIFF") ||
                            ascii.contains("ftyp") ||
                            (head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0)
                    }
            } catch (_: Exception) {
                false
            }
        }

    private fun ImportTrackJobEntity.toUiItem() = ImportTrackItem(
        trackJobId = trackJobId,
        index = trackIndex + 1,
        title = title,
        artists = artistNames,
        status = status,
        error = errorMessage,
        coverUrl = coverUrl ?: albumCover,
        durationMs = durationMs,
        musicId = musicId,
        platform = platform,
        externalId = externalId,
        lyrics = lyrics,
    )

    private fun ImportTrackJobEntity.artistList(): List<String> =
        artistNames.split(",").map { it.trim() }.filter { it.isNotBlank() }

    private fun ImportTrackJobEntity.artists(): String =
        artistNames.ifBlank { "未知艺人" }
}
