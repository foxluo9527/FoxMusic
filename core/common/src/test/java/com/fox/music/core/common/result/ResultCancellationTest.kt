package com.fox.music.core.common.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultCancellationTest {

    @Test
    fun suspendRunCatching_rethrowsCancellation() = runTest {
        try {
            suspendRunCatching {
                throw CancellationException("cancelled")
            }
            error("expected CancellationException")
        } catch (e: CancellationException) {
            assertTrue(e.message == "cancelled")
        }
    }

    @Test
    fun suspendRunCatching_wrapsRegularException() = runTest {
        val result = suspendRunCatching<Unit> {
            throw IllegalStateException("boom")
        }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is IllegalStateException)
    }
}
