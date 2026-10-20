package com.fox.music.iosshared

import com.fox.music.core.network.model.HighlightQueueItem
import kotlinx.coroutines.launch

/**
 * 音流模式单个片段（Swift 侧渲染用）。
 * 口径与 Android `feature:stream` 的 StreamClipPolicy 一致：服务端给片段就用第一条，
 * 没给就按「曲长前 30% 起、长 30s」兜底。
 */
data class FoxStreamItem(
    val musicId: Long,
    val title: String,
    val artist: String?,
    val coverUrl: String?,
    val audioUrl: String,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val label: String?,
)

/**
 * iOS 音流数据桥：Ktor REST 取队列 → 折成 Swift 友好的片段结构。
 * 与项目约定一致：不出现 suspend / Flow，全部回调风格。
 */
object FoxStreamApi {

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    fun fetchStream(
        mood: String?,
        onResult: (List<FoxStreamItem>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val items = api.fetchHighlightStream(mood = mood, userId = null, limit = 30)
                onResult(items.map { it.toFoxStreamItem() })
            } catch (e: Exception) {
                onError(e.message?.takeIf { it.isNotBlank() } ?: "音流加载失败")
            }
        }
    }
}

private fun HighlightQueueItem.toFoxStreamItem(): FoxStreamItem {
    val durRaw = duration ?: 0L
    // 与 Android StreamClipPolicy.fallbackClip 同口径：<1000 视作秒，否则视作毫秒
    val durMs = when {
        durRaw <= 0L -> 210_000L
        durRaw < 1000L -> durRaw * 1000L
        else -> durRaw
    }
    val safeDur = durMs.coerceIn(30_000L, 600_000L)
    // 与 Android StreamClipPolicy.fallbackClip 同口径：前 30% 起、长 60s（对齐服务端 60s 片段，短曲放到整曲）
    val fallbackStart = (safeDur * 0.3).toLong().coerceAtMost((safeDur - 60_000L).coerceAtLeast(0L))
    val fallbackEnd = (fallbackStart + 60_000L).coerceAtMost(safeDur)

    val clip = clips.firstOrNull()
    val startMs = clip?.startMs ?: fallbackStart
    val endMs = clip?.endMs ?: fallbackEnd
    return FoxStreamItem(
        musicId = musicId,
        title = title ?: "",
        artist = artist,
        coverUrl = coverUrl,
        audioUrl = url ?: "",
        durationMs = safeDur,
        startMs = startMs,
        endMs = endMs.coerceAtLeast(startMs + 3_000L).coerceAtMost(safeDur),
        label = clip?.label,
    )
}
