package com.fox.music.feature.player.lyric.data

import com.fox.music.core.model.music.Music

data class CustomLyricItem(
    val music: Music,
    val lyrics: String,
    val lyricsTrans: String?,
    val lyricLineCount: Int,
)
