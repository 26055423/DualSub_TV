package com.dualsub.tv.player

import com.dualsub.tv.media.SubtitleWindow
import com.dualsub.tv.subtitle.SubtitleCue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedCueCacheTest {
    private val cue = SubtitleCue(1000, 3000, "字幕")

    @Test fun `slow SMB read publishes subtitles before the window completes`() = runTest {
        val finish = CompletableDeferred<Unit>()
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, progress ->
            val window = SubtitleWindow(start, end, tracks.associateWith { listOf(cue) })
            progress(window)
            finish.await()
            window
        })
        cache.request(setOf(2), 1000)
        runCurrent()
        assertTrue(cache.state.value.isLoading)
        assertEquals(cue, cache.state.value.cues[2]!!.single())
        finish.complete(Unit)
        runCurrent()
        assertFalse(cache.state.value.isLoading)
    }

    @Test fun `late progress from previous track cannot erase the selected track`() = runTest {
        var oldProgress: ((SubtitleWindow) -> Unit)? = null
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, progress ->
            if (2 in tracks) { oldProgress = progress; awaitCancellation() }
            SubtitleWindow(start, end, tracks.associateWith { listOf(cue.copy(text = "新轨")) })
        })
        cache.request(setOf(2), 600_000)
        runCurrent()
        cache.request(setOf(3), 600_000)
        runCurrent()
        oldProgress!!(SubtitleWindow(0, 60_000, mapOf(2 to listOf(cue))))
        runCurrent()
        assertEquals(setOf(3), cache.state.value.cues.keys)
        assertEquals("新轨", cache.state.value.cues[3]!!.single().text)
    }

    @Test fun `playback advancing triggers next window before old window ends`() = runTest {
        val starts = mutableListOf<Long>()
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, _ ->
            starts += start
            SubtitleWindow(start, end, tracks.associateWith { listOf(cue) })
        })
        cache.request(setOf(2), 0)
        runCurrent()
        cache.request(setOf(2), 44_999)
        runCurrent()
        assertEquals(1, starts.size)
        cache.request(setOf(2), 45_000)
        runCurrent()
        assertEquals(2, starts.size)
        cache.request(setOf(2), 75_000)
        runCurrent()
        assertEquals(listOf(0L, 0L, 30_000L), starts)
    }

    @Test fun `both tracks use one bounded read and stationary playback does not rescan`() = runTest {
        val calls = mutableListOf<Set<Int>>()
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, _ ->
            calls += tracks
            SubtitleWindow(start, end, tracks.associateWith { listOf(cue) })
        })
        repeat(20) { cache.request(setOf(2, 3), 1000) }
        runCurrent()
        assertEquals(listOf(setOf(2, 3)), calls)
        assertFalse(cache.state.value.isLoading)
        assertEquals(setOf(2, 3), cache.state.value.cues.keys)
        repeat(20) { cache.request(setOf(2, 3), 1500) }
        runCurrent()
        assertEquals(1, calls.size)
    }

    @Test fun `seek cancels old read and only new window can publish`() = runTest {
        val oldFinishes = CompletableDeferred<Unit>()
        var calls = 0
        var concurrent = 0
        var maximum = 0
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, _ ->
            calls++
            concurrent++
            maximum = maxOf(maximum, concurrent)
            try {
                if (calls == 1) withContext(NonCancellable) { oldFinishes.await() }
                SubtitleWindow(start, end, tracks.associateWith { listOf(cue.copy(text = start.toString())) })
            } finally { concurrent-- }
        })
        cache.request(setOf(2), 0)
        runCurrent()
        cache.request(setOf(2), 3_600_000)
        runCurrent()
        assertEquals(1, calls)
        oldFinishes.complete(Unit)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, maximum)
        assertEquals("3570000", cache.state.value.cues[2]!!.single().text)
    }

    @Test fun `disabling subtitles cancels background reads`() = runTest {
        var cancelled = false
        val cache = EmbeddedCueCache(backgroundScope, read = { _, _, _, _ ->
            try { awaitCancellation() } finally { cancelled = true }
        })
        cache.request(setOf(2), 0)
        runCurrent()
        cache.request(emptySet(), 0)
        runCurrent()
        assertTrue(cancelled)
        assertTrue(cache.state.value.cues.isEmpty())
        assertFalse(cache.state.value.isLoading)
    }

    @Test fun `failures back off instead of retrying on every player tick`() = runTest {
        var attempts = 0
        var now = 0L
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, _ ->
            if (attempts++ == 0) throw IOException("network disconnected")
            SubtitleWindow(start, end, tracks.associateWith { listOf(cue) })
        }, nowMs = { now })
        cache.request(setOf(1), 0)
        runCurrent()
        assertNotNull(cache.state.value.error)
        repeat(200) { cache.request(setOf(1), 0) }
        runCurrent()
        assertEquals(1, attempts)
        now = 10_001
        cache.request(setOf(1), 0)
        runCurrent()
        assertEquals(2, attempts)
        assertNull(cache.state.value.error)
    }

    @Test fun `empty subtitle window is a valid cached result`() = runTest {
        var reads = 0
        val cache = EmbeddedCueCache(backgroundScope, read = { tracks, start, end, _ ->
            reads++
            SubtitleWindow(start, end, tracks.associateWith { emptyList() })
        })
        cache.request(setOf(2), 0)
        runCurrent()
        repeat(50) { cache.request(setOf(2), 15_000) }
        runCurrent()
        assertEquals(1, reads)
        assertNull(cache.state.value.error)
    }
}
