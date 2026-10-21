package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import kotlinx.coroutines.flow.Flow

data class PlaylistImportProgress(
    val jobId: String,
    val playlistId: Long,
    val status: String,
    val total: Int,
    val done: Int,
    val failed: Int,
    /** 当前正在处理的曲目名（曲库复用很快时可能为空） */
    val currentTitle: String? = null,
)

interface PlaylistImportProgressRepository {
    fun observe(playlistId: Long): Flow<PlaylistImportProgress?>

    /**
     * 导入详情页数据源：任务概要 + 逐首上传状态（含失败原因）。
     * 任务被清理后返回 null。
     */
    fun observeDetail(playlistId: Long): Flow<PlaylistImportDetail?>

    suspend fun pause(jobId: String)
    suspend fun resume(jobId: String)
    suspend fun retryFailed(jobId: String)
    suspend fun cancelFailed(jobId: String)

    /**
     * 只重试一首（自动链路重新跑一遍）。
     * 任务处于暂停态时无效：paused 任务下的排队曲目不会被 Worker 领取。
     */
    suspend fun retryTrack(trackJobId: String)

    /**
     * 手动指定音源完成一条失败曲目（音源纠错同款链路）：
     * 服务端转存音源 → 建/复用歌曲 → 加回歌单 → 标记该曲目已导入。
     *
     * @return 成功时为入库的 musicId
     */
    suspend fun applyManualSource(
        trackJobId: String,
        url: String,
        referer: String? = null,
        durationMs: Long? = null,
    ): Result<Long>

    /**
     * 把详情页兜底拉到的原曲歌词回写缓存，下次打开直接读库。
     * 只在原来为空时写入。
     */
    suspend fun cacheTrackLyrics(trackJobId: String, lyrics: String)
}

/** 导入详情页的逐首条目 */
data class ImportTrackItem(
    val trackJobId: String,
    val index: Int,
    val title: String,
    val artists: String,
    /** queued / running / completed / failed / paused */
    val status: String,
    val error: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val musicId: Long? = null,
    /** 导入时记录的平台与平台歌曲 id：用于拉「原曲歌词」做试听对照 */
    val platform: String? = null,
    val externalId: String? = null,
    /**
     * 原曲歌词（LRC 原文，入队时预取落库）。
     * 详情页试听候选音源时用它对照「听到的是不是这首歌」，不依赖实时网络。
     */
    val lyrics: String? = null,
)

/** 导入详情：任务概要 + 全部曲目 */
data class PlaylistImportDetail(
    val jobId: String,
    val playlistId: Long,
    val playlistTitle: String,
    val status: String,
    val total: Int,
    val done: Int,
    val failed: Int,
    val tracks: List<ImportTrackItem>,
) {
    val remaining: Int get() = tracks.count { it.status in PENDING_STATUSES }
    val isFinished: Boolean get() = remaining == 0

    companion object {
        val PENDING_STATUSES = setOf("queued", "running", "paused")
    }
}
