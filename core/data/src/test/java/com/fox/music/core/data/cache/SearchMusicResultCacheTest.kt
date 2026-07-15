package com.fox.music.core.data.cache

import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchMusicResultCacheTest {

    @Test
    fun clear_removesEntries() {
        val cache = SearchMusicResultCache()
        val page = PagedData(listOf(Music(id = 1, title = "t", url = "u")), total = 1, current = 1)
        cache.put("hello", null, 1, 20, page)
        assertNotNull(cache.get("hello", null, 1, 20))
        cache.clear()
        assertNull(cache.get("hello", null, 1, 20))
    }
}
