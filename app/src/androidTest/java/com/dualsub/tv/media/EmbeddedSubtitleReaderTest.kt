package com.dualsub.tv.media

import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dualsub.tv.subtitle.SubtitleFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.IOException
import java.io.File

@RunWith(AndroidJUnit4::class)
class EmbeddedSubtitleReaderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun embeddedAssPreservesEffectsAndSrtStillDecodes() = runBlocking(Dispatchers.IO) {
        val bytes = fixture()
        val sources = mutableListOf<BytesSource>()
        val source = { BytesSource(bytes, maxRead = 17).also { sources += it } }
        val tracks = EmbeddedSubtitleReader.listTracks(context, source)
        assertEquals(2, tracks.size)
        val ass = tracks.single { it.format == SubtitleFormat.ASS }
        assertEquals("text/x-ssa", ass.mimeType)
        assertEquals("zh", ass.language) // Media3 normalizes ISO-639 language codes.
        var progress = 0
        val cues = EmbeddedSubtitleReader.readCues(context, source, ass.index) { progress++ }
        assertEquals(2, cues.size)
        assertTrue(progress > 0)
        assertEquals(1000L, cues.first().startMs)
        assertEquals(3500L, cues.first().endMs)
        assertEquals("Hello,世界", cues.first().text)
        val effect = requireNotNull(cues.first().assOverride)
        assertEquals(0.5f, effect.posX!!, 0.001f)
        assertEquals(0.25f, effect.posY!!, 0.001f)
        assertEquals(200, effect.fadeInMs)
        assertEquals(true, effect.spans.first().bold)
        assertEquals(1f, cues.last().assOverride!!.moveToX!!, 0f)
        val srt = tracks.single { it.format == SubtitleFormat.SRT }
        val plain = EmbeddedSubtitleReader.readCues(context, source, srt.index)
        assertEquals(listOf("第一句", "第二句"), plain.map { it.text })
        assertEquals(listOf(1000L, 5000L), plain.map { it.startMs })
        assertEquals(listOf(3500L, 7500L), plain.map { it.endMs })
        assertTrue(sources.all { it.closed })
    }

    @Test fun cancellationClosesSourceAndDoesNotBecomeEmptySuccess() = runBlocking(Dispatchers.IO) {
        val bytes = fixture()
        val source = BytesSource(bytes)
        try {
            EmbeddedSubtitleReader.readCues(context, { source }, 0) {
                throw CancellationException("leave player")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(source.closed)
        }
    }

    @Test fun sourceFailureIsVisibleAndClosesSource() = runBlocking(Dispatchers.IO) {
        val source = object : MediaDataSource() {
            var closed = false
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                throw IOException("SMB disconnected")
            override fun getSize() = 1000L
            override fun close() { closed = true }
        }
        try {
            EmbeddedSubtitleReader.listTracks(context, { source })
            fail("Read error must not become an empty track list")
        } catch (_: IOException) {
            assertTrue(source.closed)
        }
    }

    /** Optional smoke test against a supplied local movie; never bundled with the app. */
    @Test fun suppliedLocalMovieDecodesChineseTrack() = runBlocking(Dispatchers.IO) {
        val path = InstrumentationRegistry.getArguments().getString("mediaPath")
        assumeTrue("No local movie supplied", path != null)
        val uri = Uri.fromFile(File(requireNotNull(path)))
        val source = { LocalMediaDataSource(context, uri) }
        val tracks = EmbeddedSubtitleReader.listTracks(context, source)
        assertTrue(tracks.isNotEmpty())
        val selected = tracks.firstOrNull { it.language?.startsWith("zh") == true } ?: tracks.first()
        val cues = EmbeddedSubtitleReader.readCues(context, source, selected.index)
        assertTrue(cues.isNotEmpty())
        assertTrue(cues.all { it.endMs >= it.startMs })
        for (start in listOf(0L, 600_000L)) {
            val before = System.nanoTime()
            val window = EmbeddedSubtitleReader.readWindow(context, source, setOf(selected.index), start, start + 60_000)
            val expected = cues.filter { it.endMs >= start && it.startMs <= start + 60_000 }
            assertEquals(expected, window.cues[selected.index])
            Log.i("DualSubTV", "实片窗口：${start / 1000}s，${window.bytesRead} 字节，" +
                "${(System.nanoTime() - before) / 1_000_000}ms，${expected.size} 条")
        }
        Log.i("DualSubTV", "回归实片：${tracks.size} 轨，#${selected.index} ${selected.format}，" +
            "${cues.size} 条，首条=${cues.first().startMs}-${cues.first().endMs} ${cues.first().text}")
        Unit
    }

    @Test fun midStreamFailureIsNotCachedAsTruncatedSuccess() = runBlocking(Dispatchers.IO) {
        val source = BytesSource(fixture(), maxRead = 17)
        var delivered = false
        try {
            EmbeddedSubtitleReader.readCues(context, { source }, 0) {
                delivered = true
                source.fail = true
            }
            fail("MediaExtractorCompat must not hide a source error as EOF")
        } catch (error: IOException) {
            assertEquals("connection lost", error.message)
            assertTrue(delivered)
            assertTrue(source.closed)
        }
    }

    private class BytesSource(val bytes: ByteArray, val maxRead: Int = Int.MAX_VALUE) : MediaDataSource() {
        var closed = false
        var fail = false
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (fail) throw IOException("connection lost")
            if (size == 0) return 0
            if (position >= bytes.size) return -1
            val count = minOf(size, maxRead, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun getSize() = bytes.size.toLong()
        override fun close() { closed = true }
    }

    // A real, tiny Matroska stream: two subtitle tracks and two timed clusters.
    // Kept in source so the test needs no downloaded/private movie or FFmpeg install.
    private fun fixture(): ByteArray {
        val header = element(0x1A45DFA3, uint(0x4286, 1) + uint(0x42F7, 1) +
            uint(0x42F2, 4) + uint(0x42F3, 8) + text(0x4282, "matroska") +
            uint(0x4287, 4) + uint(0x4285, 2))
        val assHeader = """
            [Script Info]
            PlayResX: 1920
            PlayResY: 1080
            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,48
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        """.trimIndent()
        fun track(number: Int, codec: String, lang: String, privateData: String = "") =
            element(0xAE, uint(0xD7, number) + uint(0x73C5, number) + uint(0x83, 17) +
                text(0x86, codec) + text(0x22B59C, lang) +
                if (privateData.isNotEmpty()) text(0x63A2, privateData) else byteArrayOf())
        fun block(track: Int, payload: String) = element(0xA0,
            element(0xA1, byteArrayOf((0x80 or track).toByte(), 0, 0, 0) + payload.toByteArray()) +
                uint(0x9B, 2500))
        fun cluster(time: Int, ass: String, srt: String) = element(0x1F43B675,
            uint(0xE7, time) + block(1, ass) + block(2, srt))
        val segment = element(0x1549A966, uint(0x2AD7B1, 1_000_000)) +
            element(0x1654AE6B, track(1, "S_TEXT/ASS", "chi", assHeader) +
                track(2, "S_TEXT/UTF8", "eng")) +
            cluster(1000, """0,0,Default,,0,0,0,,{\pos(960,270)\fad(200,300)\b1}Hello,世界""", "第一句") +
            element(0xEC, ByteArray(128 * 1024)) +
            cluster(5000, """1,0,Default,,0,0,0,,{\move(0,0,1920,1080)}移动""", "第二句")
        return header + element(0x18538067, segment)
    }

    private fun text(id: Int, value: String) = element(id, value.toByteArray())
    private fun uint(id: Int, value: Int) = element(id, bigEndian(value))
    private fun bigEndian(value: Int): ByteArray {
        val size = (1..4).first { value.toLong() < (1L shl (8 * it)) }
        return ByteArray(size) { (value ushr (8 * (size - it - 1))).toByte() }
    }
    private fun element(id: Int, value: ByteArray): ByteArray {
        val size = (1..4).first { value.size.toLong() < (1L shl (7 * it)) - 1 }
        val encodedSize = value.size or (1 shl (7 * size))
        val vint = ByteArray(size) { (encodedSize ushr (8 * (size - it - 1))).toByte() }
        return bigEndian(id) + vint + value
    }
}
