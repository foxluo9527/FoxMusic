package com.fox.music.core.data.scrape

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 本地音频时长探测（导入准确性校验用）。
 *
 * 背景（2026-10-21）：音源站/API 源都存在「拿到错版本」的可能——网易云 VIP
 * 只给 30s 试听、网页站点给整张专辑或串烧、同名翻唱/伴奏混进候选。歌单平台
 * 元数据自带官方时长，下载后实测比对即可挡掉大部分错版本。
 *
 * 两档判定（务必区分，否则会把「时长略有差异的正常版本」全误杀）：
 * - [acceptable] 硬闸门：实测不得短于原曲 60% —— 挡 30s 试听/半首截断，
 *   这类几乎不可能误杀（正常版本差异都在 ±5% 内）；
 * - [isClose] 软判定：偏差 <= max(6s, 10%) —— 只用于排序/日志，
 *   不单独作为拒绝依据（跨平台/不同母带差异可能超过 10%）。
 *
 * 只做读操作，失败一律返回 true / null（不阻断导入主流程）。
 */
object AudioDurationProbe {

    /** 试听/截断判定系数：实测时长 < 原曲 60% 视为片段 */
    private const val MIN_FULL_RATIO = 0.6

    /** 读取本地音频时长（毫秒）；读不到返回 null */
    fun probeMs(file: File): Long? {
        if (!file.exists() || file.length() < 1024) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 硬闸门：能否接受这个音频（挡住试听片段/半首截断）。
     * 任一侧时长未知视为可接受（不猜、不阻断）。
     */
    fun acceptable(actualMs: Long?, expectedMs: Long?): Boolean {
        val actual = actualMs ?: return true
        val expected = expectedMs ?: return true
        if (actual <= 0 || expected <= 0) return true
        return actual >= expected * MIN_FULL_RATIO
    }

    /**
     * 软判定：时长是否精确吻合（偏差 <= max(6s, 10%)）。
     * 仅用于日志/排序提示，**不作为拒绝依据**。
     */
    fun isClose(actualMs: Long?, expectedMs: Long?): Boolean {
        val actual = actualMs ?: return true
        val expected = expectedMs ?: return true
        if (actual <= 0 || expected <= 0) return true
        val diff = kotlin.math.abs(actual - expected)
        return diff <= maxOf(6_000L, (maxOf(actual, expected) * 0.1).toLong())
    }
}
