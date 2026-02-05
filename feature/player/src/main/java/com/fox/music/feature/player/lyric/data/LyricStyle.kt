package com.fox.music.feature.player.lyric.data

import android.view.Gravity

data class LyricStyle(
    val fontSize: Int = 18,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val highlightColor: Int = 0xFFFF6B6B.toInt(),
    val backgroundColor: Int = 0x99000000.toInt(),
    val isBold: Boolean = false,
    val alignment: Int = Gravity.CENTER,
    val isScrollEnabled: Boolean = true,
    val fadeDuration: Int = 300,
    val lineSpacing: Float = 1.5f
)