package com.fox.music.core.common.lyric

import android.content.Context

object LyricStyleChangeNotifier {
    @Volatile
    var onLyricStyleChanged: ((Context) -> Unit)? = null

    fun notifyChanged(context: Context) {
        onLyricStyleChanged?.invoke(context.applicationContext)
    }
}
