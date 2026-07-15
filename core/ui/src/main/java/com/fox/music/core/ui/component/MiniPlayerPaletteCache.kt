package com.fox.music.core.ui.component

import androidx.compose.ui.graphics.Color

/**
 * MiniPlayer 前景色缓存。各 Tab 的 NavHost 目的地会各自挂载 MiniPlayer，
 * 切换 Tab 时 composable 会重建；缓存可避免重复 Palette 取色导致的颜色闪变。
 */
object MiniPlayerPaletteCache {
    private val lock = Any()
    private val contrastColors = object : LinkedHashMap<String, Color>(
        MAX_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean =
            size > MAX_ENTRIES
    }

    fun cacheKey(musicId: Long, coverUrl: String?): String =
        "v2|$musicId|${coverUrl.orEmpty()}"

    fun getContrast(musicId: Long, coverUrl: String?): Color? =
        synchronized(lock) { contrastColors[cacheKey(musicId, coverUrl)] }

    fun putContrast(musicId: Long, coverUrl: String?, color: Color) {
        synchronized(lock) {
            contrastColors[cacheKey(musicId, coverUrl)] = color
        }
    }

    private const val MAX_ENTRIES = 64
}
