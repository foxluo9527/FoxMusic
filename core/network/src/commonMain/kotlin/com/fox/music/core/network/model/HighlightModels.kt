package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 音流模式服务端响应模型（与 routes/highlight.js 的 envelope 对齐）。
 * 服务端返回 { code, message, data:{ queue:[...] }, success }，
 * 由 ApiResponse<HighlightStreamData> 承载。
 */
@Serializable
data class HighlightStreamData(
    val queue: List<HighlightQueueItem> = emptyList(),
)

@Serializable
data class HighlightQueueItem(
    @SerialName("music_id") val musicId: Long,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val url: String? = null,
    val duration: Long? = null,
    /** 高潮片段（毫秒窗口 + 中文情绪标签）；为空时客户端用兜底 clip 播放 */
    val clips: List<HighlightClipDto> = emptyList(),
)

@Serializable
data class HighlightClipDto(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    /** omni 给的中文情绪/内容描述；dsp/energy 为 null */
    val label: String? = null,
    /** omni / dsp / energy */
    val source: String? = null,
    @SerialName("energy_ratio") val energyRatio: Float? = null,
    val score: Float? = null,
)

@Serializable
data class HighlightSegmentsData(
    val segments: Map<String, List<HighlightClipDto>> = emptyMap(),
)
