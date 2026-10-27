package com.fox.music.core.player.lyric

/**
 * 预取只补「还没有歌词」的曲子。
 * 用户已经应用过覆盖，或当前曲目已有歌词时，不能用曲库结果盖掉。
 */
fun shouldApplyPrefetchedLyrics(hasOverride: Boolean, currentLyrics: String?): Boolean {
    if (hasOverride) return false
    return currentLyrics.isNullOrBlank()
}
