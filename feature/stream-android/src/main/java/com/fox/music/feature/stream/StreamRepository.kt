package com.fox.music.feature.stream

import com.fox.music.core.network.api.HighlightApiService
import com.fox.music.core.network.model.HighlightQueueItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音流队列数据层：封装 /api/highlight/stream 调用。
 * 成功返回 queue；失败抛异常，由 ViewModel 兜成 Error 态。
 */
@Singleton
class StreamRepository @Inject constructor(
    private val api: HighlightApiService,
) {
    suspend fun getStream(
        mood: String?,
        userId: Long?,
        limit: Int,
        seed: String? = null,
        exclude: List<Long> = emptyList(),
    ): List<HighlightQueueItem> {
        val resp = api.getStream(
            mood = mood?.takeIf { it.isNotBlank() },
            userId = userId,
            limit = limit,
            seed = seed,
            exclude = exclude.takeIf { it.isNotEmpty() }?.joinToString(","),
        )
        val data = resp.data
        if (resp.isSuccess && data != null) return data.queue
        throw IllegalStateException(resp.message.ifBlank { "音流队列获取失败" })
    }
}
