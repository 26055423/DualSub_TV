package com.dualsub.tv.media

import android.media.MediaDataSource
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.CueDecoder
import com.dualsub.tv.subtitle.EmbeddedAssParser
import com.dualsub.tv.subtitle.SubtitleCue
import com.dualsub.tv.subtitle.SubtitleFormat
import com.dualsub.tv.subtitle.sortedAndDistinct
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.IOException

internal data class SubtitleWindow(
    val startMs: Long,
    val endMs: Long,
    val cues: Map<Int, List<SubtitleCue>>,
    val bytesRead: Long = 0
)

/** One bounded, indexed pass for both subtitle slots. No video/audio payload is downloaded. */
internal class SparseMatroskaReader private constructor(
    private val input: RandomAccessExtractorInput,
    private val selected: Set<Int>,
    private val listOnly: Boolean,
    private val startMs: Long,
    private val endMs: Long,
    private val onProgress: (SubtitleWindow) -> Unit = {}
) {
    private val tracks = linkedMapOf<Int, Sink>()
    private var seekMap: SeekMap? = null
    private var lastProgressNs = 0L
    private var deliveredCount = 0
    private var preparing = true
    private var timeScaleNs = 1_000_000L
    private val position = PositionHolder()
    private var segmentStart = 0L
    private data class IndexedCue(val track: Int, val timeTicks: Long, val cluster: Long,
        val relative: Long, val durationTicks: Long?)
    private val subtitleIndex = ArrayList<IndexedCue>()
    private var cueTime = 0L
    private var cueTrack = -1
    private var cueCluster = -1L
    private var cueRelative = -1L
    private var cueDuration: Long? = null
    private var indexedBlock = false
    private class Boundary : RuntimeException()

    private val extractor = object : MatroskaExtractor(EmbeddedSubtitleReader.subtitleParserFactory()) {
        fun setClusterTimestamp(ticks: Long) { super.integerElement(0xE7, ticks) }
        private var ignoredGroup = false
        private val header = ByteArray(8)

        override fun getElementType(id: Int): Int = when (id) {
            0xF0, 0xB2 -> 2 // CueRelativePosition / CueDuration: unsigned integer.
            else -> super.getElementType(id)
        }

        override fun integerElement(id: Int, value: Long) {
            when (id) {
                0xB3 -> cueTime = value
                0xF7 -> cueTrack = value.toInt()
                0xF1 -> cueCluster = value
                0xF0 -> { cueRelative = value; return }
                0xB2 -> { cueDuration = value; return }
            }
            if (id == 0x2AD7B1) timeScaleNs = value
            if (id == 0xE7) {
                if (preparing) throw Boundary()
                // Block timestamps are signed 16-bit relative to the cluster timestamp.
                // Include that possible negative offset before ending the window.
                if ((value - 32768L) * timeScaleNs / 1000 > endMs * 1000) throw Boundary()
            }
            super.integerElement(id, value)
        }

        override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
            if (id == 0x18538067) segmentStart = contentPosition
            if (id == 0xB7) { cueTrack = -1; cueCluster = -1; cueRelative = -1; cueDuration = null }
            if (id == 0xA0) ignoredGroup = false
            super.startMasterElement(id, contentPosition, contentSize)
        }

        override fun endMasterElement(id: Int) {
            if (id == 0xA0 && ignoredGroup) { ignoredGroup = false; return }
            super.endMasterElement(id)
            if (id == 0xB7 && tracks[cueTrack]?.index in selected) {
                subtitleIndex += IndexedCue(cueTrack, cueTime, cueCluster, cueRelative, cueDuration)
            }
            if (id == 0xA0 && indexedBlock) throw Boundary()
            if (id == 0x1654AE6B && listOnly) throw Boundary()
        }

        override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
            if (id == 0xA1 || id == 0xA3) {
                // Peek only the track-number VINT, then let Media3 handle selected blocks
                // (duration, header stripping, encoding, lacing, ASS initialization, etc.).
                input.resetPeekPosition()
                input.peekFully(header, 0, 1)
                val first = header[0].toInt() and 255
                val width = (1..8).firstOrNull { first and (0x80 ushr (it - 1)) != 0 }
                    ?: throw IOException("无效的 MKV 轨道编号")
                if (width > contentSize) throw IOException("MKV Block 长度不足")
                if (width > 1) input.peekFully(header, 1, width - 1)
                var number = (first and (0xFF ushr width)).toLong()
                for (i in 1 until width) number = (number shl 8) or (header[i].toLong() and 255)
                input.resetPeekPosition()
                val sink = tracks[number.toInt()]
                if (preparing || sink == null || sink.index !in selected || sink.isBitmap) {
                    input.skipFully(contentSize)
                    if (id == 0xA1) ignoredGroup = true
                    return
                }
            }
            if (id == 0xA5 && ignoredGroup) { input.skipFully(contentSize); return }
            super.binaryElement(id, contentSize, input)
        }
    }

    init {
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput =
                tracks.getOrPut(id) { Sink(tracks.size) }
            override fun endTracks() = Unit
            override fun seekMap(map: SeekMap) { seekMap = map }
        })
    }

    private fun pump() {
        while (true) {
            when (extractor.read(input, position)) {
                Extractor.RESULT_END_OF_INPUT -> return
                Extractor.RESULT_SEEK -> input.reposition(position.position)
            }
            if (!preparing) {
                val count = tracks.values.sumOf { it.cues.size }
                val now = System.nanoTime()
                if (count > deliveredCount && (deliveredCount == 0 || now - lastProgressNs >= 250_000_000)) {
                    onProgress(snapshot())
                    deliveredCount = count
                    lastProgressNs = now
                }
            }
        }
    }

    private fun prepare() {
        try { pump() } catch (_: Boundary) { /* tracks, then index up to the first cluster */ }
    }

    private fun trackInfos(): List<EmbeddedSubtitleReader.TrackInfo> = tracks.values.mapNotNull { sink ->
        val format = sink.format ?: return@mapNotNull null
        val original = if (format.sampleMimeType == SubtitleFormat.MIME_MEDIA3_CUES) format.codecs
            else format.sampleMimeType
        val kind = SubtitleFormat.fromMimeType(original).takeIf { it != SubtitleFormat.UNKNOWN }
            ?: SubtitleFormat.fromMimeType(format.sampleMimeType)
        if (kind == SubtitleFormat.UNKNOWN) return@mapNotNull null
        EmbeddedSubtitleReader.TrackInfo(sink.index, original, format.language, format.label, kind)
    }

    private fun window(): SubtitleWindow {
        val began = System.nanoTime()
        prepare()
        android.util.Log.i("DualSubTV", "MKV index ready ms=${(System.nanoTime()-began)/1_000_000} bytes=${input.bytesRead}")
        val available = trackInfos().map { it.index }.toSet()
        require(selected.all { it in available }) { "所选内嵌字幕轨已不存在，请重新选轨" }
        if (canReadIndexed()) {
            readIndexed()
            return snapshot()
        }
        val map = seekMap ?: throw IOException("MKV 没有可用的时间索引")
        if (!map.isSeekable && startMs > 0) throw IOException("MKV 缺少跳转索引，无法定位此处字幕；可使用外挂字幕")
        val point = map.getSeekPoints(startMs * 1000).first
        android.util.Log.i("DualSubTV", "MKV seek start=$startMs position=${point.position} timeUs=${point.timeUs}")
        preparing = false
        extractor.seek(point.position, point.timeUs)
        input.reposition(point.position)
        try { pump() } catch (_: Boundary) { /* stop after the requested time window */ }
        return snapshot()
    }

    private fun canReadIndexed(): Boolean {
        val textTracks = tracks.filterValues { it.index in selected && !it.isBitmap }.keys
        return textTracks.all { id ->
            val entries = subtitleIndex.filter { it.track == id }
            entries.isNotEmpty() && entries.all { it.cluster >= 0 && it.relative >= 0 }
        }
    }

    private data class Element(val id: Long, val start: Long, val end: Long)
    private fun readVint(id: Boolean = false): Long {
        val b = ByteArray(1)
        input.readFully(b, 0, 1)
        val first = b[0].toInt() and 255
        val width = (1..8).firstOrNull { first and (128 ushr (it - 1)) != 0 }
            ?: throw IOException("Invalid EBML VINT")
        var value = if (id) first.toLong() else (first and (255 ushr width)).toLong()
        repeat(width - 1) { input.readFully(b, 0, 1); value = (value shl 8) or (b[0].toLong() and 255) }
        return value
    }
    private fun element(): Element {
        val id = readVint(true)
        val size = readVint()
        val start = input.position
        require(size <= input.length - start) { "MKV element outside file" }
        return Element(id, start, start + size)
    }
    private fun clusterInfo(position: Long): Pair<Long, Long> {
        input.reposition(position)
        val cluster = element()
        require(cluster.id == 0x1F43B675L) { "MKV subtitle index does not point to a cluster" }
        while (input.position < cluster.end) {
            val child = element()
            if (child.id == 0xE7L) {
                require(child.end - child.start in 1..8)
                val bytes = ByteArray((child.end - child.start).toInt())
                input.readFully(bytes, 0, bytes.size)
                var ticks = 0L
                for (b in bytes) ticks = (ticks shl 8) or (b.toLong() and 255)
                return cluster.start to ticks
            }
            input.reposition(child.end)
        }
        throw IOException("MKV cluster has no timestamp")
    }
    private fun readIndexed() {
        preparing = false
        val clusters = hashMapOf<Long, Pair<Long, Long>>()
        val entries = subtitleIndex.filter {
            val timeMs = it.timeTicks * timeScaleNs / 1_000_000
            val end = it.durationTicks?.let { duration -> timeMs + duration * timeScaleNs / 1_000_000 }
            timeMs <= endMs && (end == null || end >= startMs) && tracks[it.track]?.isBitmap != true
        }.sortedBy { it.timeTicks }
        for (cue in entries) {
            val (payload, ticks) = clusters.getOrPut(cue.cluster) { clusterInfo(segmentStart + cue.cluster) }
            val at = payload + cue.relative
            input.reposition(at)
            val block = element()
            require(block.id == 0xA0L || block.id == 0xA3L) { "MKV subtitle index does not point to a block" }
            extractor.seek(at, cue.timeTicks * timeScaleNs / 1000)
            extractor.setClusterTimestamp(ticks)
            input.reposition(at)
            indexedBlock = true
            try { extractor.read(input, position) } catch (_: Boundary) { /* One BlockGroup only. */ }
            finally { indexedBlock = false }
            onProgress(snapshot())
        }
        android.util.Log.i("DualSubTV", "MKV subtitle direct index: ${entries.size} blocks, ${input.bytesRead} bytes")
    }

    private fun snapshot() = SubtitleWindow(startMs, endMs,
            tracks.values.filter { it.index in selected }.associate { sink ->
                sink.index to sink.cues.filter { it.endMs >= startMs && it.startMs <= endMs }.sortedAndDistinct()
            }, input.bytesRead)

    private inner class Sink(val index: Int) : TrackOutput {
        var format: Format? = null
        val isBitmap: Boolean get() = SubtitleFormat.isBitmapMime(
            if (format?.sampleMimeType == SubtitleFormat.MIME_MEDIA3_CUES) format?.codecs else format?.sampleMimeType
        )
        private var ass: EmbeddedAssParser? = null
        private val bytes = ByteArrayOutputStream()
        val cues = ArrayList<SubtitleCue>()
        override fun format(format: Format) {
            this.format = format
            if (SubtitleFormat.fromMimeType(format.sampleMimeType) == SubtitleFormat.ASS) {
                ass = EmbeddedAssParser(format.initializationData)
            }
        }
        override fun sampleData(reader: DataReader, length: Int, allowEndOfInput: Boolean, part: Int): Int {
            val data = ByteArray(minOf(length, 65536))
            val count = reader.read(data, 0, data.size)
            if (count < 0) { if (allowEndOfInput) return -1 else throw java.io.EOFException() }
            append(data, count)
            return count
        }
        override fun sampleData(data: ParsableByteArray, length: Int, part: Int) {
            val sample = ByteArray(length)
            data.readBytes(sample, 0, length)
            append(sample, length)
        }
        private fun append(data: ByteArray, length: Int) {
            if (bytes.size().toLong() + length > 16 * 1024 * 1024) throw IOException("字幕样本过大")
            bytes.write(data, 0, length)
        }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            val all = bytes.toByteArray()
            require(size >= 0 && offset >= 0 && size + offset <= all.size)
            val sample = all.copyOfRange(all.size - offset - size, all.size - offset)
            bytes.reset()
            bytes.write(all, all.size - offset, offset)
            if (preparing || index !in selected) return
            if (cryptoData != null) throw IOException("不支持加密字幕轨")
            if (ass != null) { cues += ass!!.parse(sample, timeUs); return }
            if (format?.sampleMimeType != SubtitleFormat.MIME_MEDIA3_CUES) throw IOException("不支持此字幕编码")
            val timing = CueDecoder().decode(timeUs, sample, 0, sample.size)
            val start = if (timing.startTimeUs != C.TIME_UNSET) timing.startTimeUs else timeUs
            val end = timing.endTimeUs.takeIf { it != C.TIME_UNSET && it > start } ?: (start + 3_000_000)
            for (cue in timing.cues) {
                if (cue.bitmap != null) continue
                val text = cue.text?.toString().orEmpty()
                if (text.isNotBlank()) cues += SubtitleCue(start / 1000, end / 1000, text)
            }
        }
    }

    companion object {
        suspend fun listTracks(source: MediaDataSource): List<EmbeddedSubtitleReader.TrackInfo> {
            val coroutine = currentCoroutineContext()
            val reader = SparseMatroskaReader(RandomAccessExtractorInput(source, { coroutine.ensureActive() }),
                emptySet(), true, 0, 0)
            try { reader.prepare(); return reader.trackInfos() } finally { reader.extractor.release() }
        }
        suspend fun readWindow(source: MediaDataSource, tracks: Set<Int>, startMs: Long, endMs: Long,
            onProgress: (SubtitleWindow) -> Unit = {}): SubtitleWindow {
            val coroutine = currentCoroutineContext()
            val reader = SparseMatroskaReader(RandomAccessExtractorInput(source, { coroutine.ensureActive() }),
                tracks, false, startMs, endMs, onProgress)
            try { return reader.window() } finally { reader.extractor.release() }
        }
    }
}
