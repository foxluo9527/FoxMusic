package com.fox.music.core.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fox.music.core.common.lyric.LyricColorPreferences
import com.fox.music.core.common.lyric.LyricColors

@Composable
fun rememberLyricColors(): LyricColors {
    val context = LocalContext.current
    return remember(context) { LyricColorPreferences.read(context) }
}

fun LyricColors.highlightComposeColor(): Color = Color(highlightColor)

fun LyricColors.textComposeColor(): Color = Color(textColor)
