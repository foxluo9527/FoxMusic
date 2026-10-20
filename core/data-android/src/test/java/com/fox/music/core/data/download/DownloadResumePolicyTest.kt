package com.fox.music.core.data.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumePolicyTest {

    @Test
    fun `restart when server ignores range with 200`() {
        assertTrue(DownloadResumePolicy.shouldRestartFromBeginning(httpCode = 200, existingBytes = 1024))
    }

    @Test
    fun `do not restart on fresh download with 200`() {
        assertFalse(DownloadResumePolicy.shouldRestartFromBeginning(httpCode = 200, existingBytes = 0))
    }

    @Test
    fun `do not restart on partial content 206`() {
        assertFalse(DownloadResumePolicy.shouldRestartFromBeginning(httpCode = 206, existingBytes = 1024))
    }
}
