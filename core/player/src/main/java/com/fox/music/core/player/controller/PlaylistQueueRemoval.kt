package com.fox.music.core.player.controller

import com.fox.music.core.model.music.PlaybackSourceKind

/**
 * 从播放队列里删掉若干曲目后，剩余队列和应落到的下标。
 *
 * [nextIndex] 指向剩余列表：删的是当前曲时落到原下一首（已经是最后一首则落到新的最后一首）；
 * 删的不是当前曲时仍停在同一首。队列被删空时 [nextIndex] 为 -1。
 */
data class QueueRemovalPlan(
    val keptOriginalIndices: List<Int>,
    val nextIndex: Int,
    val removedCurrent: Boolean,
)

fun planQueueRemoval(
    size: Int,
    currentIndex: Int,
    shouldRemove: (Int) -> Boolean,
): QueueRemovalPlan {
    if (size <= 0) return QueueRemovalPlan(emptyList(), -1, false)
    val safe = currentIndex.coerceIn(0, size - 1)
    val removedCurrent = shouldRemove(safe)
    val kept = ArrayList<Int>(size)
    var removedBefore = 0
    for (index in 0 until size) {
        if (shouldRemove(index)) {
            if (index < safe) removedBefore++
        } else {
            kept.add(index)
        }
    }
    if (kept.isEmpty()) return QueueRemovalPlan(emptyList(), -1, removedCurrent)
    val next = (safe - removedBefore).coerceIn(0, kept.lastIndex)
    return QueueRemovalPlan(kept, next, removedCurrent)
}

/**
 * 当前播放队列是不是这一份歌单。
 * 详情页点播、个人页「播放全部」用 `collection_detail/{id}`；
 * 助手用 `assistant:playlist/{id}`；来源卡片带 PLAYLIST + id 时也算同一份。
 */
fun playbackTargetsPlaylist(
    playingKey: String?,
    playlistKey: String,
    sourceKind: PlaybackSourceKind?,
    sourceId: Long?,
    playlistId: Long,
): Boolean {
    if (playlistId <= 0L) return false
    if (playlistKey.isNotEmpty() && playingKey == playlistKey) return true
    if (playingKey == "collection_detail/$playlistId") return true
    if (playingKey == "assistant:playlist:$playlistId") return true
    return sourceKind == PlaybackSourceKind.PLAYLIST && sourceId == playlistId
}
