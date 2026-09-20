package com.dualsub.tv.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleTrackLoadingTest {
    @Test fun `transient first failure discovers tracks without manual reload`() = runTest {
        var attempts = 0
        var retries = 0
        val tracks = retrySubtitleTrackRead(onRetry = { retries++ }) {
            if (++attempts == 1) throw IOException("cold SMB connection")
            listOf(2, 3)
        }
        assertEquals(listOf(2, 3), tracks)
        assertEquals(2, attempts)
        assertEquals(1, retries)
    }

    @Test fun `repeated failure stops instead of looping forever`() = runTest {
        var attempts = 0
        try {
            retrySubtitleTrackRead(onRetry = {}) { attempts++; throw IOException("offline") }
            fail("Must fail after retry")
        } catch (_: IOException) { }
        assertEquals(2, attempts)
    }

    @Test fun `exit while waiting for retry prevents another read`() = runTest {
        var attempts = 0
        val job = backgroundScope.launch {
            retrySubtitleTrackRead(onRetry = {}) { attempts++; throw IOException("temporary") }
        }
        runCurrent()
        job.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(1, attempts)
    }

    @Test fun `empty track list and unsupported formats do not retry`() = runTest {
        var attempts = 0
        assertTrue(retrySubtitleTrackRead(onRetry = { fail("No retry on success") }) {
            attempts++; emptyList<Int>()
        }.isEmpty())
        assertEquals(1, attempts)
        try {
            retrySubtitleTrackRead(onRetry = { fail("No retry on format error") }) {
                attempts++; throw IllegalArgumentException("invalid format")
            }
            fail("Must propagate format error")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, attempts)
    }
}
