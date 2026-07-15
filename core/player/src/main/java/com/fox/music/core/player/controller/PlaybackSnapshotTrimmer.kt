package com.fox.music.core.player.controller

import com.fox.music.core.model.music.PlaybackSnapshot

/** 持久化快照时剥离非当前曲目的大字段，降低 DataStore / OOM 风险。 */
internal object PlaybackSnapshotTrimmer {
    fun forPersist(snapshot: PlaybackSnapshot): PlaybackSnapshot {
        if (snapshot.playlist.isEmpty()) return snapshot
        val currentIndex = snapshot.currentIndex.coerceIn(0, snapshot.playlist.lastIndex)
        val currentId = snapshot.playlist[currentIndex].id
        return snapshot.copy(
            playlist = snapshot.playlist.map { music ->
                if (music.id == currentId) {
                    music
                } else {
                    music.copy(lyrics = null, lyricsTrans = null)
                }
            },
        )
    }
}
