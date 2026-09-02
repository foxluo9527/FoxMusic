package com.fox.music.core.data.player

import com.fox.music.core.model.music.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 校验队列签名对歌词变化敏感，避免仅进度更新时误跳过 Room 写入。
 */
class PlaybackQueueSignatureTest {

    @Test
    fun signature_changesWhenLyricsChange() {
        val base = Music(id = 1, title = "a", url = "u", lyrics = null)
        val withLyrics = base.copy(lyrics = "[00:00.00]hello")
        assertNotEquals(signature(listOf(base)), signature(listOf(withLyrics)))
    }

    @Test
    fun signature_stableForSamePlaylist() {
        val playlist = listOf(
            Music(id = 1, title = "a", url = "u1", lyrics = "x", isFavorite = true),
            Music(id = 2, title = "b", url = "u2", lyrics = "y"),
        )
        assertEquals(signature(playlist), signature(playlist.map { it.copy() }))
    }

    private fun signature(playlist: List<Music>): String =
        playlist.joinToString(separator = "|") { music ->
            buildString {
                append(music.id)
                append(':')
                append(music.url.hashCode())
                append(':')
                append(music.lyrics?.length ?: -1)
                append(':')
                append(music.lyricsTrans?.length ?: -1)
                append(':')
                append(if (music.isFavorite) 1 else 0)
            }
        }
}
