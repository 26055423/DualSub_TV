package com.dualsub.tv.media

import android.media.MediaDataSource
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.TreeMap

@RunWith(AndroidJUnit4::class)
class SparseMatroskaReaderTest {
    @Test fun twentyGiBContainerSkipsVideoAndSeeksBothSubtitleTracksInOnePass() = runBlocking(Dispatchers.IO) {
        val source = largeFixture()
        assertTrue(source.size > 20L * 1024 * 1024 * 1024)
        val tracks = SparseMatroskaReader.listTracks(source)
        assertEquals(listOf(1, 2), tracks.map { it.index })
        val partials = mutableListOf<SubtitleWindow>()
        val window = SparseMatroskaReader.readWindow(source, setOf(1, 2), 2_700_000, 2_760_000) {
            partials += it
        }
        assertTrue("Deliver subtitles before completing the scan", partials.isNotEmpty())
        assertTrue(partials.first().bytesRead < window.bytesRead)
        assertTrue(partials.first().cues.values.flatten().isNotEmpty())
        assertEquals(listOf("ASS 45", "ASS 46"), window.cues[1]!!.map { it.text })
        assertEquals(listOf("SRT 45", "SRT 46"), window.cues[2]!!.map { it.text })
        assertEquals(0.5f, window.cues[1]!!.first().assOverride!!.posX!!, 0.001f)
        assertTrue("Read ${source.transferred} bytes from a 20 GiB container", source.transferred < 512 * 1024)
        Log.i("DualSubTV", "大文件回归：逻辑 ${source.size} 字节，列轨+45分钟双字幕窗口实际读取 ${source.transferred} 字节")
        Unit
    }

    @Test fun subtitleRelativeIndexMatchesFallbackAndPreservesAss() = runBlocking(Dispatchers.IO) {
        val expected = SparseMatroskaReader.readWindow(largeFixture(), setOf(1, 2), 2_700_000, 2_760_000)
        val indexed = SparseMatroskaReader.readWindow(largeFixture(withSubtitleIndex = true), setOf(1, 2), 2_700_000, 2_760_000)
        assertFalse(expected.usesSubtitleIndex)
        assertTrue("Must exercise direct subtitle index, not silently fall back", indexed.usesSubtitleIndex)
        assertEquals(expected.cues, indexed.cues)
        assertTrue(indexed.bytesRead < 100_000)
        val empty = SparseMatroskaReader.readWindow(largeFixture(withSubtitleIndex = true), setOf(1, 2), 10_000, 20_000)
        assertTrue(empty.cues.values.all { it.isEmpty() })
    }

    @Test fun pgsTrackDoesNotAbortSelectedTextTrack() = runBlocking(Dispatchers.IO) {
        val source = largeFixture(withPgs = true)
        val tracks = SparseMatroskaReader.listTracks(source)
        assertTrue(tracks.single { it.index == 3 }.isBitmap)
        assertTrue(tracks.single { it.index == 3 }.label.contains("图片字幕，暂不支持"))
        val window = SparseMatroskaReader.readWindow(source, setOf(2, 3), 2_700_000, 2_760_000)
        assertEquals(listOf("SRT 45", "SRT 46"), window.cues[2]!!.map { it.text })
        assertTrue(window.cues[3]!!.isEmpty())
    }

    @Test fun dialogueFreeWindowFinishesWithoutScanningToNextSubtitle() = runBlocking(Dispatchers.IO) {
        val source = largeFixture()
        val window = SparseMatroskaReader.readWindow(source, setOf(1, 2), 10_000, 20_000)
        assertTrue(window.cues.values.all { it.isEmpty() })
        assertTrue(source.transferred < 256 * 1024)
    }

    @Test fun randomInputPeekShortReadAndLargeSkipKeepIndependentPositions() {
        val source = object : MediaDataSource() {
            var readBytes = 0
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position >= this.size) return -1
                val n = minOf(size, 3, (this.size - position).toInt())
                repeat(n) { buffer[offset + it] = ((position + it) % 256).toByte() }
                readBytes += n
                return n
            }
            override fun getSize() = 3_000_000L
            override fun close() = Unit
        }
        val input = RandomAccessExtractorInput(source, {})
        val bytes = ByteArray(10)
        input.peekFully(bytes, 0, bytes.size)
        assertEquals(0L, input.position)
        assertEquals(10L, input.peekPosition)
        input.resetPeekPosition()
        input.readFully(bytes, 0, bytes.size)
        assertEquals(10L, input.position)
        input.skipFully(2_000_000)
        assertEquals(2_000_010L, input.position)
        input.readFully(bytes, 0, 3)
        assertEquals((2_000_010 % 256).toByte(), bytes[0])
        assertTrue(source.readBytes < 100)
    }

    /** Sparse logical file: 80 clusters with 256 MiB video blocks, no huge allocation. */
    private fun largeFixture(withPgs: Boolean = false, withSubtitleIndex: Boolean = false): SparseSource {
        val source = SparseSource()
        val ebml = element(0x1A45DFA3, uint(0x4286, 1) + uint(0x42F7, 1) +
            uint(0x42F2, 4) + uint(0x42F3, 8) + text(0x4282, "matroska") + uint(0x4287, 4) + uint(0x4285, 2))
        val info = element(0x1549A966, uint(0x2AD7B1, 1_000_000) +
            element(0x4489, ByteBuffer.allocate(8).putDouble(4_800_000.0).array()))
        val assHeader = "[Script Info]\nPlayResX: 1920\nPlayResY: 1080\n[Events]\n"
        fun track(id: Int, type: Int, codec: String, extra: ByteArray = byteArrayOf()) = element(0xAE,
            uint(0xD7, id.toLong()) + uint(0x73C5, id.toLong()) + uint(0x83, type.toLong()) + text(0x86, codec) + extra)
        val tracks = element(0x1654AE6B,
            track(1, 1, "V_VP8", element(0xE0, uint(0xB0, 1920) + uint(0xBA, 1080))) +
                track(2, 17, "S_TEXT/ASS", text(0x63A2, assHeader)) + track(3, 17, "S_TEXT/UTF8") +
                if (withPgs) track(4, 17, "S_HDMV/PGS") else byteArrayOf())
        fun seekHead(at: Long) = element(0x114D9B74, element(0x4DBB,
            element(0x53AB, bytes(0x1C53BB6B, 4)) + element(0x53AC, bytes(at, 8))))
        val metaSize = info.size + tracks.size + seekHead(0).size
        var relative = metaSize.toLong()
        val clusters = mutableListOf<Pair<Long, List<Pair<Long, ByteArray>>>>()
        val clusterPositions = mutableListOf<Long>()
        val subtitleOffsets = mutableListOf<Pair<Long, Long>>()
        val videoSize = 256L * 1024 * 1024
        fun sub(track: Int, payload: String) = element(0xA0,
            element(0xA1, byteArrayOf((0x80 or track).toByte(), 0, 0, 0) + payload.toByteArray()) + uint(0x9B, 2500))
        repeat(80) { n ->
            clusterPositions += relative
            val time = uint(0xE7, n * 60_000L)
            val videoHeader = header(0xA3, videoSize + 4) + byteArrayOf(0x81.toByte(), 0, 0, 0x80.toByte())
            val subtitles = sub(2, "0,0,Default,,0,0,0,,{\\pos(960,270)}ASS $n") + sub(3, "SRT $n") +
                if (withPgs) sub(4, "ignored bitmap payload") else byteArrayOf()
            val firstOffset = time.size + videoHeader.size + videoSize
            subtitleOffsets += firstOffset to (firstOffset + sub(2, "0,0,Default,,0,0,0,,{\\pos(960,270)}ASS $n").size)
            val clusterHeader = header(0x1F43B675, time.size + videoHeader.size + videoSize + subtitles.size)
            val leading = clusterHeader + time + videoHeader
            clusters += relative to listOf(0L to leading, leading.size + videoSize to subtitles)
            relative += leading.size + videoSize + subtitles.size
        }
        val cuesAt = relative
        val cues = element(0x1C53BB6B, clusterPositions.mapIndexed { index, pos ->
            element(0xBB, uint(0xB3, index * 60_000L) + element(0xB7, uint(0xF7, 1) + uint(0xF1, pos))) +
                if (withSubtitleIndex) listOf(2 to subtitleOffsets[index].first, 3 to subtitleOffsets[index].second)
                    .map { (track, offset) -> element(0xBB, uint(0xB3, index * 60_000L) +
                        element(0xB7, uint(0xF7, track.toLong()) + uint(0xF1, pos) + uint(0xF0, offset) + uint(0xB2, 2500))) }
                    .fold(byteArrayOf()) { a, b -> a + b } else byteArrayOf()
        }.fold(byteArrayOf()) { a, b -> a + b })
        val segmentHeader = header(0x18538067, relative + cues.size)
        source.put(0, ebml + segmentHeader)
        val segmentBase = (ebml.size + segmentHeader.size).toLong()
        source.put(segmentBase, info + tracks + seekHead(cuesAt))
        clusters.forEach { (at, parts) -> parts.forEach { (offset, data) -> source.put(segmentBase + at + offset, data) } }
        source.put(segmentBase + cuesAt, cues)
        return source
    }

    private class SparseSource : MediaDataSource() {
        val parts = TreeMap<Long, ByteArray>()
        var transferred = 0L
        fun put(position: Long, bytes: ByteArray) { parts[position] = bytes }
        override fun getSize(): Long = requireNotNull(parts.lastEntry()).let { it.key + it.value.size }
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= this.size) return -1
            val count = minOf(size.toLong(), this.size - position).toInt()
            buffer.fill(0, offset, offset + count)
            for ((start, data) in parts) {
                val from = maxOf(position, start)
                val to = minOf(position + count, start + data.size)
                if (to > from) data.copyInto(buffer, offset + (from - position).toInt(), (from - start).toInt(), (to - start).toInt())
            }
            transferred += count
            return count
        }
        override fun close() = Unit
    }
    private fun bytes(value: Long, size: Int) = ByteArray(size) { (value ushr (8 * (size - it - 1))).toByte() }
    private fun uint(id: Long, value: Long) = element(id, bytes(value, (1..8).first { it == 8 || value < (1L shl (8 * it)) }))
    private fun text(id: Long, text: String) = element(id, text.toByteArray())
    private fun header(id: Long, size: Long): ByteArray {
        val idSize = (1..4).first { id < (1L shl (8 * it)) }
        val width = (1..8).first { size < (1L shl (7 * it)) - 1 }
        return bytes(id, idSize) + bytes(size or (1L shl (7 * width)), width)
    }
    private fun element(id: Long, data: ByteArray) = header(id, data.size.toLong()) + data
}
