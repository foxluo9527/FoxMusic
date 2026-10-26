package com.fox.music.core.model.music

import kotlinx.serialization.Serializable

data class PlayerState(
    val currentMusic: Music? = null,
    val playlist: List<Music> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading : Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val shuffleMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isFavorite: Boolean = false,
    /** 当前播放队列来源；弹窗仅在 [PlaybackPlaylistSource.isDisplayable] 时展示 */
    val playlistSource: PlaybackPlaylistSource? = null,
    /**
     * 音流模式当前激活的高潮片段窗口；为 null 表示普通播放。
     * UI 据此把进度条/时间显示为「片段内相对进度」，并展示中文情绪标签。
     */
    val clip: ClipRange? = null,
)

/**
 * 音流模式的高潮片段时间窗口（毫秒，锚定解码实际时长）。
 * 播放器区间播放：setMediaItem(item, clip.startMs) 传绝对起点，轮询到 clip.endMs 即 advance。
 */
@Serializable
data class ClipRange(
    val startMs: Long,
    val endMs: Long,
    /** omni 给的中文情绪/内容描述；dsp/energy 为 null */
    val label: String? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val isValid: Boolean get() = endMs > startMs && durationMs >= 1000L
}

enum class RepeatMode {
    /** 顺序播放，播完停止 */
    SEQUENTIAL,
    /** 列表循环 */
    ALL,
    /** 单曲循环 */
    ONE,
    /** 随机播放 */
    RANDOM,
}