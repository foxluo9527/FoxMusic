package com.fox.music.core.player.controller

import com.fox.music.core.model.music.PlaybackSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistQueueRemovalTest {

    @Test
    fun removingCurrentTrack_skipsToNext() {
        val plan = planQueueRemoval(size = 4, currentIndex = 1) { it == 1 }
        assertEquals(listOf(0, 2, 3), plan.keptOriginalIndices)
        assertEquals(1, plan.nextIndex)
        assertTrue(plan.removedCurrent)
    }

    @Test
    fun removingCurrentLastTrack_fallsBackToNewLast() {
        val plan = planQueueRemoval(size = 3, currentIndex = 2) { it == 2 }
        assertEquals(listOf(0, 1), plan.keptOriginalIndices)
        assertEquals(1, plan.nextIndex)
        assertTrue(plan.removedCurrent)
    }

    @Test
    fun removingCurrentAndEarlierTrack_landsOnOriginalNext() {
        val plan = planQueueRemoval(size = 4, currentIndex = 2) { it == 0 || it == 2 }
        assertEquals(listOf(1, 3), plan.keptOriginalIndices)
        assertEquals(1, plan.nextIndex)
        assertTrue(plan.removedCurrent)
    }

    @Test
    fun removingTrackBeforeCurrent_keepsSameSong() {
        val plan = planQueueRemoval(size = 3, currentIndex = 1) { it == 0 }
        assertEquals(listOf(1, 2), plan.keptOriginalIndices)
        assertEquals(0, plan.nextIndex)
        assertFalse(plan.removedCurrent)
    }

    @Test
    fun removingEveryTrack_emptiesQueue() {
        val plan = planQueueRemoval(size = 2, currentIndex = 0) { true }
        assertTrue(plan.keptOriginalIndices.isEmpty())
        assertEquals(-1, plan.nextIndex)
        assertTrue(plan.removedCurrent)
    }

    @Test
    fun playbackTargetsPlaylist_matchesKeySourceAndAssistant() {
        assertTrue(
            playbackTargetsPlaylist(
                playingKey = "collection_detail/5",
                playlistKey = "collection_detail/5",
                sourceKind = null,
                sourceId = null,
                playlistId = 5L,
            )
        )
        assertTrue(
            playbackTargetsPlaylist(
                playingKey = "assistant:playlist:5",
                playlistKey = "collection_detail/5",
                sourceKind = null,
                sourceId = null,
                playlistId = 5L,
            )
        )
        assertTrue(
            playbackTargetsPlaylist(
                playingKey = "search",
                playlistKey = "collection_detail/5",
                sourceKind = PlaybackSourceKind.PLAYLIST,
                sourceId = 5L,
                playlistId = 5L,
            )
        )
        assertFalse(
            playbackTargetsPlaylist(
                playingKey = "collection_detail/6",
                playlistKey = "collection_detail/5",
                sourceKind = PlaybackSourceKind.ALBUM,
                sourceId = 5L,
                playlistId = 5L,
            )
        )
        assertFalse(
            playbackTargetsPlaylist(
                playingKey = "car_mode_playlist",
                playlistKey = "collection_detail/5",
                sourceKind = null,
                sourceId = null,
                playlistId = 5L,
            )
        )
    }
}
