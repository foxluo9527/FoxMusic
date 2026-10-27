package com.fox.music.core.model.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricCorrectionTest {

    private val query = LyricQuery(title = "海阔天空", artist = "Beyond", durationMs = 326_000L)

    @Test
    fun plainTextIsSpreadAcrossDuration() {
        val prepared = LyricTextNormalizer.prepare("第一行\n第二行\n第三行", durationMs = 90_000L)
        assertTrue(prepared.ok)
        assertFalse(prepared.synced)
        assertEquals(3, prepared.lineCount)
        assertEquals(3, LyricsParser.parseLrc(prepared.lrc).size)
        assertTrue(prepared.lrc.startsWith("[00:00.00]第一行"))
    }

    @Test
    fun lrcWithoutFractionAndRepeatedTagsAreKept() {
        val prepared = LyricTextNormalizer.prepare(
            "[ti:海阔天空]\n[00:12]今天我\n[00:18.5][00:42]寒夜里",
        )
        assertTrue(prepared.ok)
        assertTrue(prepared.synced)
        assertEquals(3, prepared.lineCount)
        assertTrue(prepared.lrc.contains("[00:12.00]今天我"))
        assertTrue(prepared.lrc.contains("[00:18.50]寒夜里"))
        assertTrue(prepared.lrc.contains("[00:42.00]寒夜里"))
        assertFalse(prepared.lrc.contains("[ti:"))
    }

    @Test
    fun emptyAndMetadataOnlyAreRejected() {
        assertEquals("歌词内容为空", LyricTextNormalizer.prepare("  \n").message)
        assertEquals("没有可用的歌词正文", LyricTextNormalizer.prepare("[ti:只有标题]\n[ar:某人]").message)
    }

    @Test
    fun eachSourceKeepsOnlyTopMatchesAndNeteaseIsSmaller() {
        val hits = (1..8).map { index ->
            hit(title = "海阔天空", artist = "Beyond", lyrics = "相同开头但是第${index}段歌词正文完全不同啊")
        } + hit(title = "光辉岁月", artist = "Beyond", lyrics = "这是另一首歌的歌词不应该出现在结果里啊")
        val netease = LyricCandidateRanker.select(query, hits, LyricCandidateRanker.NETEASE_LIMIT)
        val open = LyricCandidateRanker.select(query, hits, LyricCandidateRanker.OPEN_LIMIT)
        assertEquals(3, netease.size)
        assertEquals(5, open.size)
        assertTrue(netease.all { it.hit.title == "海阔天空" })
        assertTrue(open.all { it.hit.title == "海阔天空" })
    }

    @Test
    fun sameLyricFromTwoSourcesCollapsesToOne() {
        val lyric = "[00:01.00]今天我 寒夜里看雪飘过\n[00:06.00]怀着冷却了的心窝漂远方"
        val groups = listOf(
            listOf(ScoredLyricHit(hit(source = "netease", lyrics = lyric, trans = "翻译"), 0.9)),
            listOf(ScoredLyricHit(hit(source = "lrclib", lyrics = lyric), 0.91)),
        )
        val merged = LyricCandidateRanker.merge(groups)
        assertEquals(1, merged.size)
        assertEquals("netease", merged.first().hit.source)
        assertEquals("翻译", merged.first().hit.lyricsTrans)
    }

    @Test
    fun bestMatchLyricIsDroppedFromTheList() {
        val lyric = "[00:01.00]今天我 寒夜里看雪飘过\n[00:06.00]怀着冷却了的心窝漂远方"
        val other = "[00:01.00]另一段完全不同的歌词内容在这里\n[00:06.00]第二行也不一样所以要留下来"
        val items = listOf(
            ScoredLyricHit(hit(lyrics = lyric), 0.9),
            ScoredLyricHit(hit(lyrics = other, title = "海阔天空"), 0.8),
        )
        val left = LyricCandidateRanker.dropMatching(items, lyric)
        assertEquals(1, left.size)
        assertTrue(left.first().hit.lyrics.contains("另一段"))
    }

    @Test
    fun lrclibSkipsInstrumentalAndKeepsSynced() {
        val hits = LyricSourceParsers.parseLrclib(
            """
            [
              {"id":1,"trackName":"海阔天空","artistName":"Beyond","duration":326,"instrumental":true,"syncedLyrics":"[00:01.00]忽略"},
              {"id":2,"trackName":"海阔天空","artistName":"Beyond","duration":326,"instrumental":false,"syncedLyrics":"[00:01.00]今天我\n[00:05.00]寒夜里","plainLyrics":"今天我"}
            ]
            """.trimIndent()
        )
        assertEquals(1, hits.size)
        assertEquals("2", hits.first().sourceId)
        assertTrue(hits.first().synced)
        assertTrue(hits.first().lyrics.contains("今天我"))
    }

    @Test
    fun megalobizSearchKeepsPageAndLrcUrls() {
        val hits = LyricSourceParsers.parseMegalobizSearch(
            """
            {"count":1,"results":[{"id":9,"title":"海阔天空","artist":"Beyond","duration":320.5,"url":"https://megalobiz.com/lrc/9/sky","lrc_url":"https://api.megalobiz.com/api/public/v1/lrc/9?format=lrc"}]}
            """.trimIndent()
        )
        assertEquals(1, hits.size)
        assertEquals("https://megalobiz.com/lrc/9/sky", hits.first().attributionUrl)
        assertEquals("https://api.megalobiz.com/api/public/v1/lrc/9?format=lrc", hits.first().fetchUrl)
        assertEquals(320_500L, hits.first().durationMs)
    }

    @Test
    fun fragmentTitleDoesNotOutrankTheRealSong() {
        val real = LyricCandidateRanker.score(query, hit(title = "海阔天空", artist = "Beyond"))
        val fragment = LyricCandidateRanker.score(
            LyricQuery("Still Alive", "Jonathan Coulton"),
            hit(title = "Alive", artist = "Someone Else", lyrics = "not the portal song lyrics at all here"),
        )
        assertTrue(real > fragment)
        assertTrue(fragment < LyricCandidateRanker.MIN_SCORE)
    }

    private fun hit(
        source: String = "netease",
        title: String = "海阔天空",
        artist: String = "Beyond",
        lyrics: String = "[00:01.00]今天我\n[00:05.00]寒夜里",
        trans: String? = null,
    ) = LyricHit(
        source = source,
        title = title,
        artist = artist,
        durationMs = 326_000L,
        lyrics = lyrics,
        lyricsTrans = trans,
    )
}
