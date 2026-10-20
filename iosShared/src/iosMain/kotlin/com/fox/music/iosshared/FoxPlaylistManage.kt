package com.fox.music.iosshared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.fox.music.core.network.model.ParsedTrackDto
import kotlinx.coroutines.launch

/**
 * 歌单相关页面的跨端刷新信号：
 * - [profileRevision]：「我的」页歌单列表（创建/删除/编辑保存/导入建单后 bump）；
 * - [detailRevision]：歌单详情页头部（编辑保存后 bump，详情页 LaunchedEffect 监听）。
 *
 * Swift 侧保存/删除成功后调用 [refreshProfile] / [refreshDetail]，Compose 侧
 * LaunchedEffect 的 key 读这两个值即可自动重载，无需在 VC 间传递刷新事件。
 */
object FoxPlaylistRefresh {

    var profileRevision: Int by mutableIntStateOf(0)
        private set

    var detailRevision: Int by mutableIntStateOf(0)
        private set

    fun refreshProfile() {
        profileRevision += 1
    }

    fun refreshDetail() {
        detailRevision += 1
    }
}

/**
 * 第三方歌单后台导入队列（对齐 Android 的 PlaylistImportScheduler + PlaylistImportWorker）。
 *
 * Android 的 Worker 有三条音源链路：客户端爬虫（HifinicnScraper，依赖本机 OkHttp+JSoup）、
 * 已验证音源资产、服务端 resolve-audio 兜底。iOS 不移植爬虫，直接走服务端链路：
 *
 *   ① 曲库查找（api/music/find）命中 → 直接加歌；
 *   ② 服务端解析音源（api/import/resolve-audio）→ 按 referer 服务端转存音频
 *      （api/upload/audio?url=，绕开第三方防盗链，等价 Android 的下载+重传 ingestAudio）；
 *   ③ 拉歌词（api/import/lyrics）+ 封面转存（api/upload/image?url=），失败不阻塞；
 *   ④ 歌曲入库（api/music/find-or-create）→ 加进歌单（api/playlists/{id}/tracks）。
 *
 * 每处理完一首就上报 import-session，编辑进首页歌单的"导入中"进度展示。
 * 队列在协程里顺序执行，App 退到后台 NSURLSession 仍会继续（无持久化，杀进程即断，
 * 与 Android WorkManager 的差异可接受）。
 */
object FoxPlaylistImportQueue {

    private val scope = foxMainScope()
    private val runningPlaylistIds = mutableSetOf<Long>()

    val isRunning: Boolean get() = runningPlaylistIds.isNotEmpty()

    /**
     * 启动一个歌单的导入任务（同一歌单并发调用会被忽略）。
     * [onFinish] 在主线程回调（imported=成功数，failed=失败数）。
     */
    fun start(
        api: FoxMusicApi,
        playlistId: Long,
        platform: String,
        tracks: List<ParsedTrackDto>,
        onFinish: (imported: Int, failed: Int) -> Unit = { _, _ -> },
    ) {
        if (tracks.isEmpty()) return
        if (!runningPlaylistIds.add(playlistId)) return
        scope.launch {
            var imported = 0
            var failed = 0
            runCatching { api.updateImportSession(playlistId, tracks.size, 0, "in_progress") }
            for (track in tracks) {
                try {
                    importTrack(api, playlistId, track, platform)
                    imported++
                } catch (_: Exception) {
                    failed++
                }
                val done = imported + failed >= tracks.size
                runCatching {
                    api.updateImportSession(
                        playlistId = playlistId,
                        totalTracks = tracks.size,
                        importedTracks = imported,
                        status = if (done) "completed" else "in_progress",
                    )
                }
            }
            runningPlaylistIds.remove(playlistId)
            // Swift 回调可能在页面已销毁后被调用，单独兜底：回调异常不能反噬导入协程
            runCatching { onFinish(imported, failed) }
        }
    }

    private suspend fun importTrack(
        api: FoxMusicApi,
        playlistId: Long,
        track: ParsedTrackDto,
        platform: String,
    ) {
        val firstArtist = track.artists.firstOrNull()?.name?.trim().orEmpty()

        // ① 曲库命中直接复用，跳过解析
        val existing = api.findMusicId(track.title, firstArtist)
        if (existing != null) {
            api.addMusicToPlaylist(playlistId, existing)
            return
        }

        // ② 服务端解析音源并转存（第三方直链往往有防盗链，必须转成自有存储 URL）
        val audio = api.resolveAudio(track.title, firstArtist)
        val playUrl = try {
            api.uploadAudioByUrl(url = audio.url, referer = audio.referer)
        } catch (_: Exception) {
            audio.url // 转存失败兜底用原始直链（部分源仍可播）
        }

        // ③ 歌词（失败不阻塞）
        var lyric = ""
        var lyricTrans = ""
        val externalId = track.externalId
        if (platform.isNotBlank() && !externalId.isNullOrBlank()) {
            try {
                api.fetchImportLyrics(platform, externalId).let {
                    lyric = it.lyric
                    lyricTrans = it.lyricTrans
                }
            } catch (_: Exception) {
            }
        }

        // ④ 封面转存（失败不阻塞）
        val coverRemote = track.cover?.takeIf { it.isNotBlank() }?.let {
            runCatching { api.uploadImageByUrl(it) }.getOrNull()
        } ?: track.albumCover?.takeIf { it.isNotBlank() }?.let {
            runCatching { api.uploadImageByUrl(it) }.getOrNull()
        }

        // ⑤ 入库 + ⑥ 加歌
        val musicId = api.findOrCreateMusic(
            title = track.title,
            url = playUrl,
            coverImage = coverRemote ?: track.cover,
            durationMs = track.duration,
            artists = track.artists.map { it.name },
            lyrics = lyric,
            lyricsTrans = lyricTrans,
            albumName = track.albumName,
            albumCover = coverRemote,
            trackNumber = track.trackNumber,
            platform = platform,
            externalId = externalId,
        )
        api.addMusicToPlaylist(playlistId, musicId)
    }
}
