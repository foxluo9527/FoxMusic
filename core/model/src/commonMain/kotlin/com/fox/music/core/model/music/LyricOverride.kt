package com.fox.music.core.model.music

import kotlinx.serialization.Serializable

/**
 * 本会话里用户应用的歌词。
 * 列表刷新、退出播放页再进、进程被系统杀掉后恢复队列，都用这份覆盖曲库旧歌词。
 */
@Serializable
data class LyricOverride(
    val lyrics: String,
    val lyricsTrans: String? = null,
)
