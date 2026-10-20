package com.fox.music.feature.stream

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 音流会话快照：冷启动恢复进度用。
 *
 * 背景：/api/highlight/stream 在同 seed + 同 exclude 下返回同一批同顺序的歌曲，
 * 因此恢复 = 用快照里的 seed/exclude 重拉 → 校验 id 序列与 [ids] 完全一致 → 续播；
 * 不一致（曲库变动/换账号等）则放弃快照换一批，避免索引错位。
 */
@Serializable
data class StreamSession(
    val mood: String? = null,
    val userId: Long? = null,
    val seed: String,
    /** 当前批的播放索引（第几首） */
    val index: Int = 0,
    /** 当前曲的播放位置（整曲绝对毫秒） */
    val positionMs: Long = 0L,
    /** 本批歌曲 id 序列：重拉后按此校验是否仍是同一批 */
    val ids: List<Long> = emptyList(),
    /** 拉这批时的 exclude 集合：复现同批的必要条件 */
    val excludeIds: List<Long> = emptyList(),
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(session: StreamSession): String = json.encodeToString(session)

        fun decode(raw: String?): StreamSession? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<StreamSession>(raw) }.getOrNull()
                ?.takeIf { it.ids.isNotEmpty() }
        }
    }
}
