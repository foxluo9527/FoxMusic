package com.fox.music.core.domain.lyric

import com.fox.music.core.model.music.LyricCandidateRanker
import com.fox.music.core.model.music.LyricQuery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenLyricSourcesTest {

    @Test
    fun megalobizFetchesOnlyTheTopMatchesAndSkipsUnrelated() = runTest {
        val fetched = mutableListOf<String>()
        val http = FakeHttp { url ->
            when {
                url.contains("/search?") -> OpenLyricResponse(
                    200,
                    """
                    {"results":[
                      {"id":1,"title":"海阔天空","artist":"Beyond","duration":326,"url":"https://megalobiz.com/lrc/1/a","lrc_url":"https://api.megalobiz.com/api/public/v1/lrc/1?format=lrc"},
                      {"id":2,"title":"光辉岁月","artist":"Beyond","duration":300,"url":"https://megalobiz.com/lrc/2/b","lrc_url":"https://api.megalobiz.com/api/public/v1/lrc/2?format=lrc"},
                      {"id":3,"title":"海阔天空","artist":"Beyond","duration":330,"url":"https://megalobiz.com/lrc/3/c","lrc_url":"https://api.megalobiz.com/api/public/v1/lrc/3?format=lrc","score":1}
                    ]}
                    """.trimIndent(),
                )
                else -> {
                    fetched += url
                    OpenLyricResponse(200, "[00:01.00]今天我寒夜里看雪飘过\n[00:08.00]怀着冷却了的心窝漂远方 $url")
                }
            }
        }
        val hits = OpenLyricSources(http).megalobiz(LyricQuery("海阔天空", "Beyond", 326_000L))
        assertTrue(fetched.none { it.contains("/lrc/2") })
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.hit.attributionUrl?.contains("megalobiz.com/lrc/") == true })
        assertTrue(hits.size <= LyricCandidateRanker.OPEN_LIMIT)
        assertTrue(http.urls.all { url -> http.headersFor(url)["User-Agent"]?.contains("FoxMusic") == true })
    }

    @Test
    fun lyricsOvh404IsEmptyNotAFailure() = runTest {
        val http = FakeHttp { OpenLyricResponse(404, """{"error":"No lyrics found"}""") }
        val hits = OpenLyricSources(http).lyricsOvh(LyricQuery("没有这首", "某人"))
        assertEquals(emptyList(), hits)
    }

    @Test
    fun lrclibSendsTrackAndPrimaryArtist() = runTest {
        val http = FakeHttp {
            OpenLyricResponse(
                200,
                """[{"id":7,"trackName":"七里香","artistName":"周杰伦","duration":299,"instrumental":false,"syncedLyrics":"[00:01.00]窗外的麻雀\n[00:05.00]在电线杆上多嘴"}]""",
            )
        }
        val hits = OpenLyricSources(http).lrclib(LyricQuery("七里香", "周杰伦 / 梁静茹", 299_000L))
        assertEquals(1, hits.size)
        assertTrue(http.urls.single().contains("track_name="))
        assertTrue(http.urls.single().contains("artist_name="))
        assertTrue(http.urls.single().contains("%E5%91%A8%E6%9D%B0%E4%BC%A6"))
        assertTrue(!http.urls.single().contains("梁静茹"))
    }
}

private class FakeHttp(
    private val handler: (String) -> OpenLyricResponse,
) : OpenLyricHttp {
    val urls = mutableListOf<String>()
    private val headers = mutableMapOf<String, Map<String, String>>()

    override suspend fun getText(url: String, headers: Map<String, String>): OpenLyricResponse {
        urls += url
        this.headers[url] = headers
        return handler(url)
    }

    fun headersFor(url: String): Map<String, String> = headers[url].orEmpty()
}
