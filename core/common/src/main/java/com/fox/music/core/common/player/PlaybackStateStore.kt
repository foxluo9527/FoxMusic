package com.fox.music.core.common.player

import com.fox.music.core.model.music.PlaybackSnapshot
import com.fox.music.core.model.music.RepeatMode

interface PlaybackStateStore {
    suspend fun loadSnapshot(): PlaybackSnapshot?
    suspend fun saveSnapshot(snapshot: PlaybackSnapshot)
    suspend fun loadRepeatMode(): RepeatMode
    suspend fun saveRepeatMode(mode: RepeatMode)
}
