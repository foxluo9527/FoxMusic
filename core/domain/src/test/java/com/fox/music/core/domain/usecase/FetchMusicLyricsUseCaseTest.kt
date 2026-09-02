package com.fox.music.core.domain.usecase

import com.fox.music.core.model.music.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchMusicLyricsUseCaseTest {

    @Test
    fun resolveImportLyricParams_fromPrefixedSourceId() {
        val music = Music(
            id = -42L,
            sourceId = "qq_123456",
            sourcePlatform = "qq",
            isThirdParty = true,
            title = "t",
            url = "u",
        )
        assertEquals("qq" to "123456", FetchMusicLyricsUseCase.resolveImportLyricParams(music))
    }

    @Test
    fun resolveImportLyricParams_fromPlatformAndNumericSourceId() {
        val music = Music(
            id = -99L,
            sourceId = "7788",
            sourcePlatform = "netease",
            isThirdParty = true,
            title = "t",
            url = "u",
        )
        assertEquals("netease" to "7788", FetchMusicLyricsUseCase.resolveImportLyricParams(music))
    }

    @Test
    fun resolveImportLyricParams_returnsNullForPlatformMusic() {
        val music = Music(
            id = 100L,
            sourceId = "100",
            title = "t",
            url = "u",
        )
        assertNull(FetchMusicLyricsUseCase.resolveImportLyricParams(music))
    }
}
