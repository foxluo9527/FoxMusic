package com.fox.music.core.common.lyric

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.toColorInt

data class LyricColors(
    val textColor: Int,
    val highlightColor: Int,
)

object LyricColorPreferences {
    private const val PREFS_NAME = "LyricSettings"
    private const val KEY_TEXT_COLOR = "text_color"
    private const val KEY_HIGHLIGHT_COLOR = "highlight_color"
    private val DEFAULT_HIGHLIGHT_COLOR = "#FF6B6B".toColorInt()

    fun read(context: Context): LyricColors {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LyricColors(
            textColor = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE),
            highlightColor = prefs.getInt(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR),
        )
    }
}
