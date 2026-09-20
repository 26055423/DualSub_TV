package com.dualsub.tv.media

import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CancellationException

class ReadResourceTest {
    @Test fun `closing error does not discard discovered tracks`() {
        val closeError = IOException("SMB close failed")
        val reported = mutableListOf<Exception>()
        val tracks = Closeable { throw closeError }.useReadResult({ reported += it }) { listOf(2, 3) }
        assertEquals(listOf(2, 3), tracks)
        assertEquals(listOf(closeError), reported)
    }

    @Test fun `read failure is preserved with close error attached`() {
        val readError = IOException("read failed")
        val closeError = IOException("close failed")
        try {
            Closeable { throw closeError }.useReadResult({ fail("Must preserve read failure") }) { throw readError }
            fail("Must fail")
        } catch (error: IOException) {
            assertSame(readError, error)
            assertEquals(listOf(closeError), error.suppressed.toList())
        }
    }

    @Test fun `cancelling read closes its source and is never reported as success`() {
        var closed = false
        try {
            Closeable { closed = true }.useReadResult({ fail("Unexpected close failure") }) {
                throw CancellationException("player exited")
            }
            fail("Must cancel")
        } catch (_: CancellationException) { }
        assertTrue(closed)
    }

    @Test fun `cancellation during close is not swallowed`() {
        try {
            Closeable { throw CancellationException("cancelled") }.useReadResult({ fail("Must cancel") }) { "tracks" }
            fail("Must cancel")
        } catch (_: CancellationException) { }
    }
}
