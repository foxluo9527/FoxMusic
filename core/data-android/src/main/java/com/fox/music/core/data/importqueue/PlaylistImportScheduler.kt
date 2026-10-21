package com.fox.music.core.data.importqueue

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fox.music.core.common.result.Result
import com.fox.music.core.data.worker.PlaylistImportWorker
import com.fox.music.core.database.dao.ImportJobDao
import com.fox.music.core.database.entity.ImportPlaylistJobEntity
import com.fox.music.core.database.entity.ImportTrackJobEntity
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.ParsedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importJobDao: ImportJobDao,
    private val importRepository: ImportRepository,
) {

    /** 歌词预取用：与 Worker 队列解耦，入队后立刻返回，不拖慢导入起步 */
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enqueuePlaylistImport(
        playlistId: Long,
        playlistTitle: String,
        tracks: List<ParsedTrack>,
        platform: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()
        ImportLog.i(
            "入队导入任务 jobId=$jobId playlistId=$playlistId " +
                "「$playlistTitle」曲目=${tracks.size} platform=$platform",
        )
        importJobDao.insertPlaylistJob(
            ImportPlaylistJobEntity(
                jobId = jobId,
                playlistId = playlistId,
                playlistTitle = playlistTitle,
                status = "queued",
                total = tracks.size,
                done = 0,
                failed = 0,
                platform = platform,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val trackEntities = tracks.mapIndexed { index, track ->
            ImportTrackJobEntity(
                trackJobId = UUID.randomUUID().toString(),
                jobId = jobId,
                playlistId = playlistId,
                trackIndex = index,
                title = track.title,
                artistNames = track.artists.joinToString(", "),
                durationMs = track.durationMs,
                coverUrl = track.cover,
                albumName = track.albumName,
                albumCover = track.albumCover,
                platform = platform,
                externalId = track.externalId,
                status = "queued",
                createdAt = now,
                updatedAt = now,
            )
        }
        importJobDao.insertTrackJobs(trackEntities)
        prefetchLyrics(trackEntities)
        tracks.take(5).forEachIndexed { i, t ->
            ImportLog.i("入队曲目样例[#${i + 1}]: ${t.artists.joinToString(",")} - ${t.title}")
        }
        if (tracks.size > 5) ImportLog.i("入队曲目样例: …共 ${tracks.size} 首")
        kickWorker()
        return jobId
    }

    /**
     * 入队即预取原曲歌词并落库。
     *
     * 导入失败的曲目走不到「导入成功」那段歌词逻辑，而详情页手动换源又必须靠歌词
     * 判断「听到的是不是这首歌」。所以在解析歌单的这一刻，用 platform/externalId
     * 把歌词一次性抓下来存好：详情页直接读库，不用等网络，离线也有。
     *
     * 并发 3；歌词只是辅助信息，失败一律静默，不影响导入本身。
     */
    private fun prefetchLyrics(tracks: List<ImportTrackJobEntity>) {
        val targets = tracks.filter {
            !it.platform.isNullOrBlank() && !it.externalId.isNullOrBlank() && it.lyrics.isNullOrBlank()
        }
        if (targets.isEmpty()) {
            ImportLog.i("歌词预取跳过：无可预取曲目（缺 platform/externalId 或已有歌词）")
            return
        }
        ImportLog.i("歌词预取入队 待处理=${targets.size}/${tracks.size}")
        prefetchScope.launch {
            val gate = Semaphore(3)
            var ok = 0
            kotlinx.coroutines.coroutineScope {
                targets.forEach { track ->
                    launch {
                        gate.withPermit {
                            val platform = track.platform ?: return@withPermit
                            val externalId = track.externalId ?: return@withPermit
                            val lrc = when (val r = importRepository.fetchLyrics(platform, externalId)) {
                                is Result.Success -> r.data.lyric
                                else -> null
                            }
                            if (!lrc.isNullOrBlank()) {
                                val rows = importJobDao.fillTrackLyrics(track.trackJobId, lrc)
                                if (rows > 0) ok++
                            }
                        }
                    }
                }
            }
            ImportLog.i("歌词预取完成 成功=$ok/${targets.size}")
        }
    }

    suspend fun pause(jobId: String) {
        ImportLog.i("暂停导入任务 jobId=$jobId")
        importJobDao.pauseJob(jobId)
        kickWorker()
    }

    suspend fun resume(jobId: String) {
        ImportLog.i("恢复导入任务 jobId=$jobId")
        importJobDao.resumeJob(jobId)
        kickWorker()
    }

    suspend fun retryFailed(jobId: String) {
        ImportLog.i("重试失败曲目 jobId=$jobId")
        importJobDao.retryFailed(jobId)
        kickWorker()
    }

    suspend fun cancelFailed(jobId: String) {
        ImportLog.i("取消失败曲目任务 jobId=$jobId")
        importJobDao.cancelFailed(jobId)
    }

    fun kickWorker(forceNew: Boolean = false) {
        ImportLog.i("调度 Worker forceNew=$forceNew")
        val request = OneTimeWorkRequestBuilder<PlaylistImportWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(PlaylistImportWorker.WORK_TAG)
            .build()
        val wm = WorkManager.getInstance(context)
        if (forceNew) {
            // Worker 内部续跑：不可 REPLACE unique work，否则会取消自身
            wm.enqueue(request)
        } else {
            wm.enqueueUniqueWork(
                PlaylistImportWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
