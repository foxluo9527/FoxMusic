package com.fox.music.core.player.controller

import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackSnapshot
import com.fox.music.core.model.music.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSnapshotTrimmerTest {

    @Test
    fun forPersist_keepsOnlyCurrentTrackLyrics() {
        val playlist = listOf(
            Music(id = 1, title = "a", url = "u1", lyrics = "lyric-a"),
            Music(id = 2, title = "b", url = "u2", lyrics = "lyric-b", lyricsTrans = "trans-b"),
            Music(id = 3, title = "c", url = "u3", lyrics = "lyric-c"),
        )
        val snapshot = PlaybackSnapshot(
            playlist = playlist,
            currentIndex = 1,
            positionMs = 12_000,
            repeatMode = RepeatMode.ALL,
        )

        val trimmed = PlaybackSnapshotTrimmer.forPersist(snapshot)

        assertEquals("lyric-b", trimmed.playlist[1].lyrics)
        assertEquals("trans-b", trimmed.playlist[1].lyricsTrans)
        assertNull(trimmed.playlist[0].lyrics)
        assertNull(trimmed.playlist[2].lyrics)
    }
}
