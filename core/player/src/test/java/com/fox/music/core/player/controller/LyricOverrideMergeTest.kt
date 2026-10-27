package com.fox.music.core.player.controller

import com.fox.music.core.model.music.LyricOverride
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.lyric.shouldApplyPrefetchedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricOverrideMergeTest {

    @Test
    fun reenterWithLibraryLyrics_keepsAppliedOverride() {
        val applied = track(lyrics = "[00:01.00]刚应用的歌词", trans = "新译")
        val fromLibrary = track(lyrics = "[00:01.00]曲库旧歌词", trans = "旧译")
        val merged = mergeMusicKeepingLyricOverride(
            incoming = fromLibrary,
            previous = applied,
            override = LyricOverride(lyrics = applied.lyrics!!, lyricsTrans = applied.lyricsTrans),
        )
        assertEquals("[00:01.00]刚应用的歌词", merged.lyrics)
        assertEquals("新译", merged.lyricsTrans)
    }

    @Test
    fun overrideWinsWhenSongWasMissingFromPreviousPlaylist() {
        val fromLibrary = track(lyrics = "[00:01.00]曲库旧歌词", trans = "旧译")
        val merged = mergeMusicKeepingLyricOverride(
            incoming = fromLibrary,
            previous = null,
            override = LyricOverride(lyrics = "[00:01.00]刚应用的歌词", lyricsTrans = "新译"),
        )
        assertEquals("[00:01.00]刚应用的歌词", merged.lyrics)
        assertEquals("新译", merged.lyricsTrans)
        assertEquals(fromLibrary.url, merged.url)
    }

    @Test
    fun libraryRefreshMatchingOverride_staysOnLocalLyrics() {
        val local = "[00:01.00]刚应用的歌词"
        val merged = mergeMusicKeepingLyricOverride(
            incoming = track(lyrics = local, trans = "译"),
            previous = track(lyrics = local, trans = "译"),
            override = LyricOverride(lyrics = local, lyricsTrans = "译"),
        )
        assertEquals(local, merged.lyrics)
        assertEquals("译", merged.lyricsTrans)
    }

    @Test
    fun withoutOverride_incomingNonBlankWins() {
        val merged = mergeMusicKeepingLyricOverride(
            incoming = track(lyrics = "曲库", trans = "曲库译"),
            previous = track(lyrics = "预取", trans = "预取译"),
            override = null,
        )
        assertEquals("曲库", merged.lyrics)
        assertEquals("曲库译", merged.lyricsTrans)
    }

    @Test
    fun withoutOverride_blankIncomingKeepsPrevious() {
        val previous = track(lyrics = "已有", trans = "译")
        val merged = mergeMusicKeepingLyricOverride(
            incoming = track(lyrics = "  ", trans = ""),
            previous = previous,
            override = null,
        )
        assertEquals("已有", merged.lyrics)
        assertEquals("译", merged.lyricsTrans)
    }

    @Test
    fun withoutOverride_unchangedIncomingIsSameInstance() {
        val incoming = track(lyrics = "曲库", trans = "译")
        val merged = mergeMusicKeepingLyricOverride(
            incoming = incoming,
            previous = track(lyrics = "旧", trans = "旧译"),
            override = null,
        )
        assertSame(incoming, merged)
    }

    @Test
    fun otherSongsAreNotPinnedByADifferentOverride() {
        val incoming = track(id = 2L, lyrics = "另一首曲库歌词", trans = null)
        val merged = mergeMusicKeepingLyricOverride(
            incoming = incoming,
            previous = track(id = 2L, lyrics = "另一首旧歌词", trans = null),
            override = null,
        )
        assertEquals("另一首曲库歌词", merged.lyrics)
    }

    @Test
    fun prefetchSkipsOverrideAndExistingLyrics() {
        assertFalse(shouldApplyPrefetchedLyrics(hasOverride = true, currentLyrics = null))
        assertFalse(shouldApplyPrefetchedLyrics(hasOverride = true, currentLyrics = "已覆盖"))
        assertFalse(shouldApplyPrefetchedLyrics(hasOverride = false, currentLyrics = "已有歌词"))
        assertTrue(shouldApplyPrefetchedLyrics(hasOverride = false, currentLyrics = null))
        assertTrue(shouldApplyPrefetchedLyrics(hasOverride = false, currentLyrics = ""))
        assertTrue(shouldApplyPrefetchedLyrics(hasOverride = false, currentLyrics = "  "))
    }

    private fun track(id: Long = 7L, lyrics: String?, trans: String? = null) = Music(
        id = id,
        title = "海阔天空",
        url = "https://cdn.example/a.mp3",
        lyrics = lyrics,
        lyricsTrans = trans,
    )
}
