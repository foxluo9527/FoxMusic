package com.fox.music.feature.player.lyric.data

import com.fox.music.core.model.music.Music

data class CustomLyricItem(
    val music: Music,
    val lyrics: String,
    val lyricsTrans: String?,
    val lyricLineCount: Int,
    val matchScore: Double = 0.0,
    /** Megalobiz 等要求展示的来源页。 */
    val attributionUrl: String? = null,
    /** false：纯文本按曲目时长铺开，时间轴不是原唱对齐的。 */
    val synced: Boolean = true,
)
