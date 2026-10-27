package com.fox.music.core.common.cache

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListDiskCacheTest {

    @Test
    fun key_scopesByOwnerAndDoesNotCollideAcrossIdsOrPages() {
        val anon = ListCacheKeys.key(null, "playlists", "recommend", "p1")
        val user = ListCacheKeys.key(7L, "playlists", "recommend", "p1")
        val otherPage = ListCacheKeys.key(7L, "playlists", "recommend", "p2")
        val otherUser = ListCacheKeys.key(17L, "playlists", "recommend", "p1")
        assertEquals("uanon_playlists_recommend_p1", anon)
        assertEquals("u7_playlists_recommend_p1", user)
        assertTrue(user != otherPage)
        assertTrue(user != otherUser)
        assertTrue(otherUser.startsWith(ListCacheKeys.ownerPrefix(17L)))
        assertTrue(!otherUser.startsWith(ListCacheKeys.ownerPrefix(7L)))
    }

    @Test
    fun snapshotFile_roundTripsPayloadAndDropsExpired() {
        val dir = File.createTempFile("snap", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            SnapshotFiles.write(dir, "u7_albums_recommend_p1", expiresAtMs = 5_000L, payload = "{\"a\":1}\nline")
            val hit = SnapshotFiles.read(dir, "u7_albums_recommend_p1", nowMs = 4_000L)
            assertEquals("{\"a\":1}\nline", hit?.payload)
            assertEquals(5_000L, hit?.expiresAtMs)
            assertNull(SnapshotFiles.read(dir, "u7_albums_recommend_p1", nowMs = 5_001L))
            assertTrue(!SnapshotFiles.file(dir, "u7_albums_recommend_p1").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
