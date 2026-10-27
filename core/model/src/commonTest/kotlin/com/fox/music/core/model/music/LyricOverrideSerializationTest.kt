package com.fox.music.core.model.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class LyricOverrideSerializationTest {

    @Test
    fun longKeyMapRoundTrips() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val serializer = MapSerializer(serializer<Long>(), LyricOverride.serializer())
        val value = mapOf(42L to LyricOverride(lyrics = "[00:01.00]刚应用的歌词", lyricsTrans = "新译"))
        val raw = json.encodeToString(serializer, value)
        assertEquals(value, json.decodeFromString(serializer, raw))
    }
}
