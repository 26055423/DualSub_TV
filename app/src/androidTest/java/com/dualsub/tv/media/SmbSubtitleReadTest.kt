package com.dualsub.tv.media

import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dualsub.tv.player.EmbeddedCueCache
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.network.smb.SmbMediaDataSource
import com.dualsub.tv.network.smb.SmbSessionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmbSubtitleReadTest {
    @Test fun suppliedSmbMovieDeliversBothSelectedTracks() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("smbHost")
        assumeTrue("No SMB movie supplied", host != null)
        val path = String(Base64.decode(requireNotNull(args.getString("smbPathBase64")), Base64.DEFAULT), Charsets.UTF_8)
        val location = RemoteLocation("test", RemoteType.SMB, "test", requireNotNull(host), args.getString("smbShare"), username = args.getString("smbUsername"), password = args.getString("smbPassword"))
        val pool = SmbSessionPool()
        val sources = mutableListOf<SmbMediaDataSource>()
        val source = { SmbMediaDataSource(pool, location, path, args.getString("blockSize")?.toInt() ?: 4096).also { sources += it } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val tracks = withTimeout(30_000) { EmbeddedSubtitleReader.listTracks(context, source) }
            Log.i("DualSubTV", "SMB TEST tracks: " + tracks.joinToString { "${it.index}:${it.language}:${it.title}:${it.mimeType}" })
            val selected = args.getString("tracks")?.split(",")?.map { it.toInt() }?.toSet()
                ?: tracks.filterNot { it.isBitmap }.take(2).map { it.index }.toSet()
            assertEquals(2, selected.size)
            for (start in (args.getString("starts") ?: "0,570000,3570000").split(",").map { it.toLong() }) {
                sources.clear()
                val begin = System.nanoTime()
                val first = mutableMapOf<Int, Long>()
                try {
                    val window = withTimeout(args.getString("timeoutMs")?.toLong() ?: 25_000L) {
                        EmbeddedSubtitleReader.readWindow(context, source, selected, start, start + 90_000) { partial ->
                            partial.cues.forEach { (track, cues) ->
                                if (cues.isNotEmpty() && track !in first) {
                                    first[track] = (System.nanoTime() - begin) / 1_000_000
                                    Log.i("DualSubTV", "SMB TEST first: start=$start track=$track ms=${first[track]} cue=${cues.first()}")
                                }
                            }
                        }
                    }
                    Log.i("DualSubTV", "SMB TEST done: start=$start ms=${(System.nanoTime()-begin)/1_000_000} input=${window.bytesRead} counts=${window.cues.mapValues { it.value.size }}")
                    if (args.getString("expectSubtitleIndex") == "true") assertTrue(window.usesSubtitleIndex)
                    if (start > 0) assertTrue(window.cues.values.all { it.isNotEmpty() })
                } finally {
                    Log.i("DualSubTV", "SMB TEST IO: start=$start calls=${sources.sumOf { it.networkReadCount }} bytes=${sources.sumOf { it.networkBytesRead }}")
                }
            }
        } finally { pool.clear() }
    }

    @Test fun suppliedSmbSessionMatchesFreshReadsAcrossAdvanceSeekAndTrackSwitch() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("smbHost")
        assumeTrue("No SMB movie supplied", host != null)
        val path = String(Base64.decode(requireNotNull(args.getString("smbPathBase64")), Base64.DEFAULT), Charsets.UTF_8)
        val location = RemoteLocation("test", RemoteType.SMB, "test", requireNotNull(host), args.getString("smbShare"),
            username = args.getString("smbUsername"), password = args.getString("smbPassword"))
        val pool = SmbSessionPool()
        val sources = mutableListOf<SmbMediaDataSource>()
        val source = { SmbMediaDataSource(pool, location, path).also { sources += it } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = EmbeddedSubtitleReader.Session(context, source)
        try {
            val tracks = withTimeout(30_000) { EmbeddedSubtitleReader.listTracks(context, source) }
                .filterNot { it.isBitmap }.map { it.index }
            assumeTrue("Need three text tracks to verify switching", tracks.size >= 3)
            val initial = tracks.take(2).toSet()
            val switched = setOf(tracks[0], tracks[2])
            for ((start, selected) in listOf(570_000L to initial, 600_000L to initial,
                3_570_000L to initial, 570_000L to switched)) {
                suspend fun measured(label: String, operation: suspend () -> SubtitleWindow): SubtitleWindow {
                    val calls = sources.sumOf { it.networkReadCount }
                    val bytes = sources.sumOf { it.networkBytesRead }
                    val began = System.nanoTime()
                    val result = withTimeout(30_000) { operation() }
                    Log.i("DualSubTV", "SMB SESSION $label start=$start tracks=$selected ms=${(System.nanoTime()-began)/1_000_000}" +
                        " input=${result.bytesRead} calls=${sources.sumOf { it.networkReadCount } - calls}" +
                        " bytes=${sources.sumOf { it.networkBytesRead } - bytes} counts=${result.cues.mapValues { it.value.size }}")
                    return result
                }
                val fresh = measured("fresh") { EmbeddedSubtitleReader.readWindow(context, source, selected, start, start + 90_000) }
                val reused = measured("reused") { session.readWindow(selected, start, start + 90_000) }
                assertEquals("Session output differs at $start for $selected", fresh.cues, reused.cues)
                assertTrue(reused.cues.values.all { it.isNotEmpty() })
                if (args.getString("expectSubtitleIndex") == "true") assertTrue(reused.usesSubtitleIndex)
            }
            // Exercise coverage/reuse without assuming window alignment, size or read ordering.
            withContext(Dispatchers.Main) {
                coroutineScope {
                    val requests = mutableListOf<ReadRequest>()
                    val cache = EmbeddedCueCache(this, read = { selected, start, end, progress ->
                        requests += ReadRequest(selected.toSet(), start, end)
                        withContext(Dispatchers.IO) {
                            val began = System.nanoTime()
                            val calls = sources.sumOf { it.networkReadCount }
                            val bytes = sources.sumOf { it.networkBytesRead }
                            val window = withTimeout(30_000) { session.readWindow(selected, start, end, progress) }
                            Log.i("DualSubTV", "SMB CACHE start=$start end=$end tracks=$selected ms=${(System.nanoTime()-began)/1_000_000}" +
                                " input=${window.bytesRead} calls=${sources.sumOf { it.networkReadCount } - calls}" +
                                " bytes=${sources.sumOf { it.networkBytesRead } - bytes}")
                            window
                        }
                    })
                    suspend fun verify(position: Long, selected: Set<Int>, changingSecondary: Boolean = false) {
                        val previous = requests.toList()
                        val primaryBefore = cache.state.value.cues[tracks[0]]
                        cache.request(selected, position)
                        if (changingSecondary) assertEquals("Keep primary while changing secondary",
                            primaryBefore, cache.state.value.cues[tracks[0]])
                        val state = withTimeout(35_000) { cache.state.first { !it.isLoading } }
                        assertNull(state.error)
                        val added = requests.drop(previous.size)
                        assertNoReread(previous, added)
                        assertTrue("Must not read unselected tracks: $added", added.all { selected.containsAll(it.tracks) })
                        if (changingSecondary) {
                            assertTrue("Changing secondary must reuse primary: $added", added.none { tracks[0] in it.tracks })
                        }
                        // Probe a covered playback interval, rather than reconstructing the scheduler's buckets.
                        val end = minOf(position + 10_000, selected.minOf { track ->
                            requests.filter { track in it.tracks }.maxOf { it.end }
                        })
                        assertTrue("No forward coverage at $position", end > position)
                        selected.forEach { assertCovered(it, position, end, requests) }
                        val fresh = withContext(Dispatchers.IO) {
                            withTimeout(30_000) { EmbeddedSubtitleReader.readWindow(context, source, selected, position, end) }
                        }
                        val actual = state.cues.mapValues { (_, cues) -> cues.filter { it.endMs >= position && it.startMs <= end } }
                        assertEquals("Cache differs over $position..$end for $selected", fresh.cues, actual)
                    }
                    verify(600_000, initial)
                    val firstCoverage = requests.toList()
                    // Advance past the observed coverage, independent of the configured bucket step.
                    val oldEnd = initial.minOf { track -> firstCoverage.filter { track in it.tracks }.maxOf { it.end } }
                    val advanced = oldEnd + 1
                    verify(advanced, initial)
                    initial.forEach { assertCovered(it, oldEnd, advanced + 1, requests) }
                    verify(advanced, switched, changingSecondary = true)
                    cache.clear()
                }
            }
        } finally { session.close(); pool.clear() }
    }

    private data class ReadRequest(val tracks: Set<Int>, val start: Long, val end: Long)

    private fun assertCovered(track: Int, start: Long, end: Long, reads: List<ReadRequest>) {
        var coveredUntil = start
        for (read in reads.filter { track in it.tracks }.sortedBy { it.start }) {
            if (read.end <= coveredUntil) continue
            if (read.start > coveredUntil) break
            coveredUntil = maxOf(coveredUntil, read.end)
        }
        assertTrue("Track $track has a gap in $start..$end; coverage ends at $coveredUntil; reads=$reads",
            coveredUntil >= end)
    }

    private fun assertNoReread(previous: List<ReadRequest>, added: List<ReadRequest>) {
        val seen = previous.toMutableList()
        for (read in added) {
            // Shared endpoints are allowed so events crossing a boundary are not lost.
            val overlaps = seen.filter { old -> old.tracks.any { it in read.tracks } &&
                maxOf(old.start, read.start) < minOf(old.end, read.end) }
            assertTrue("Reread already-covered subtitles: $read overlaps $overlaps", overlaps.isEmpty())
            seen += read
        }
    }

}
