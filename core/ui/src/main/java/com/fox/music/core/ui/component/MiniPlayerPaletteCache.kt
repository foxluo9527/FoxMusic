package com.fox.music.core.ui.component

import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * MiniPlayer 前景色缓存。各 Tab 的 NavHost 目的地会各自挂载 MiniPlayer，
 * 切换 Tab 时 composable 会重建；缓存可避免重复 Palette 取色导致的颜色闪变。
 */
object MiniPlayerPaletteCache {
    private val contrastColors = ConcurrentHashMap<String, Color>()

    fun cacheKey(musicId: Long, coverUrl: String?): String = "$musicId|${coverUrl.orEmpty()}"

    fun getContrast(musicId: Long, coverUrl: String?): Color? =
        contrastColors[cacheKey(musicId, coverUrl)]

    fun putContrast(musicId: Long, coverUrl: String?, color: Color) {
        contrastColors[cacheKey(musicId, coverUrl)] = color
    }
}
