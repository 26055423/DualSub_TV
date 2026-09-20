package com.dualsub.tv.media

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import com.dualsub.tv.subtitle.EmbeddedAssParser
import com.dualsub.tv.subtitle.SubtitleCue
import com.dualsub.tv.subtitle.SubtitleFormat
import com.dualsub.tv.subtitle.sortedAndDistinct
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Media3 负责容器解析；ASS 保留原始事件和初始化数据，交给现有特效解析器。
 * 其他受支持的字幕继续由 Media3 转成 Cue。按轨按需读取，并逐批交付，
 * 不必等整个网络文件扫描完才显示第一句。失败向调用方传播，不能缓存为成功的空轨。
 */
object EmbeddedSubtitleReader {
    private const val MAX_SAMPLE_BYTES = 16 * 1024 * 1024

    data class TrackInfo(
        val index: Int,
        val mimeType: String?,
        val language: String?,
        val title: String?,
        val format: SubtitleFormat
    ) {
        val isBitmap: Boolean get() = SubtitleFormat.isBitmapMime(mimeType)
        val label: String
            get() = buildString {
                append(if (isBitmap) "图片字幕，暂不支持" else format.displayName)
                language?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                title?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                append("（内嵌 #").append(index).append('）')
            }
    }

    // 返回 false 时 Media3 原样传递样本和 csd，不会先剥掉 ASS 特效标签。
    internal fun extractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory().setSubtitleParserFactory(subtitleParserFactory())
    }

    internal fun subtitleParserFactory(): SubtitleParser.Factory {
        val default = DefaultSubtitleParserFactory()
        return object : SubtitleParser.Factory by default {
                override fun supportsFormat(format: Format): Boolean =
                    SubtitleFormat.fromMimeType(format.sampleMimeType) != SubtitleFormat.ASS &&
                        default.supportsFormat(format)
        }
    }

    suspend fun listTracks(context: Context, sourceFactory: () -> MediaDataSource): List<TrackInfo> {
        sourceFactory().useReadResult({ Log.w("DualSubTV", "关闭字幕读取源失败（保留已读结果）", it) }) { source ->
            if (isMatroska(source)) return SparseMatroskaReader.listTracks(source)
        }
        return listTracksCompat(context, sourceFactory)
    }

    internal suspend fun readWindow(
        context: Context, sourceFactory: () -> MediaDataSource,
        tracks: Set<Int>, startMs: Long, endMs: Long,
        onProgress: (SubtitleWindow) -> Unit = {}
    ): SubtitleWindow {
        require(startMs >= 0 && endMs > startMs)
        sourceFactory().useReadResult({ Log.w("DualSubTV", "关闭字幕读取源失败（保留已读结果）", it) }) { source ->
            if (isMatroska(source)) return SparseMatroskaReader.readWindow(source, tracks, startMs, endMs, onProgress)
        }
        // Other supported containers retain the Media3 path, but never scan to EOF.
        val cues = linkedMapOf<Int, List<SubtitleCue>>()
        for (track in tracks) cues[track] = readCues(context, sourceFactory, track, startMs, endMs)
        return SubtitleWindow(startMs, endMs, cues)
    }

    /**
     * Lifetime of one video; calls and close must be serialized on the subtitle I/O mutex.
     * Only Matroska reuses its source, extractor and index. Other containers keep the
     * bounded MediaExtractorCompat fallback, opening fresh sources/extractors per read.
     * Track/time cue reuse is handled separately by EmbeddedCueCache for either path.
     */
    internal class Session(private val context: Context, private val sourceFactory: () -> MediaDataSource) : java.io.Closeable {
        private var source: MediaDataSource? = null
        private var reader: SparseMatroskaReader? = null
        private var nonMatroska = false
        private var closed = false

        suspend fun readWindow(tracks: Set<Int>, startMs: Long, endMs: Long,
            onProgress: (SubtitleWindow) -> Unit = {}): SubtitleWindow {
            check(!closed) { "字幕会话已关闭" }
            try {
                if (reader == null && !nonMatroska) {
                    val opened = sourceFactory()
                    source = opened
                    if (isMatroska(opened)) reader = SparseMatroskaReader.open(opened)
                    else { nonMatroska = true; discardReader() }
                }
                return reader?.readNext(tracks, startMs, endMs, onProgress)
                    ?: EmbeddedSubtitleReader.readWindow(context, sourceFactory, tracks, startMs, endMs, onProgress)
            } catch (failure: Throwable) {
                // A cancelled/failed extractor can contain a partial sample or EBML element.
                // Retry with a fresh source; never reuse that half-initialized state.
                try { discardReader() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }

        private fun discardReader() {
            val oldReader = reader
            val oldSource = source
            reader = null
            source = null
            try { oldReader?.close() } finally { oldSource?.close() }
        }

        override fun close() { closed = true; discardReader() }
    }

    private fun isMatroska(source: MediaDataSource): Boolean {
        val bytes = ByteArray(4)
        var read = 0
        while (read < bytes.size) {
            val count = source.readAt(read.toLong(), bytes, read, bytes.size - read)
            if (count <= 0) return false
            read += count
        }
        return bytes.contentEquals(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
    }

    private suspend fun listTracksCompat(context: Context, sourceFactory: () -> MediaDataSource): List<TrackInfo> =
        withExtractor(context, sourceFactory) { extractor ->
            (0 until extractor.trackCount).mapNotNull { index ->
                val mediaFormat = extractor.getTrackFormat(index)
                val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
                val originalMime = mediaFormat.getString(MediaFormat.KEY_CODECS_STRING) ?: mime
                val format = SubtitleFormat.fromMimeType(originalMime)
                    .takeIf { it != SubtitleFormat.UNKNOWN }
                    ?: SubtitleFormat.fromMimeType(mime)
                if (format == SubtitleFormat.UNKNOWN) return@mapNotNull null
                TrackInfo(index, originalMime,
                    mediaFormat.getString(MediaFormat.KEY_LANGUAGE),
                    mediaFormat.getString("label") ?: mediaFormat.getString("title"), format)
            }
        }

    /** onProgress 在读取线程收到不可变快照；调用方负责切回 UI 线程。 */
    suspend fun readCues(
        context: Context,
        sourceFactory: () -> MediaDataSource,
        trackIndex: Int,
        startMs: Long = 0,
        endMs: Long = Long.MAX_VALUE,
        onProgress: suspend (List<SubtitleCue>) -> Unit = {}
    ): List<SubtitleCue> = withExtractor(context, sourceFactory,
        byteBudget = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else 16L * 1024 * 1024
    ) { extractor ->
        require(trackIndex in 0 until extractor.trackCount) { "字幕轨 #$trackIndex 已不存在" }
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (SubtitleFormat.isBitmapMime(format.getString(MediaFormat.KEY_CODECS_STRING) ?: mime)) return@withExtractor emptyList()
        val assParser = if (SubtitleFormat.fromMimeType(mime) == SubtitleFormat.ASS) {
            val initialization = generateSequence(0) { it + 1 }
                .takeWhile { format.containsKey("csd-$it") }
                .map { index ->
                    val buffer = requireNotNull(format.getByteBuffer("csd-$index")).duplicate()
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }.toList()
            EmbeddedAssParser(initialization)
        } else null
        if (assParser == null && mime != SubtitleFormat.MIME_MEDIA3_CUES) {
            throw IOException("尚不支持该内嵌字幕编码：$mime")
        }
        extractor.selectTrack(trackIndex)
        if (startMs > 0) extractor.seekTo(startMs * 1000, MediaExtractorCompat.SEEK_TO_PREVIOUS_SYNC)
        val decoder = CueDecoder()
        val cues = ArrayList<SubtitleCue>()
        var buffer = ByteBuffer.allocate(64 * 1024)
        var lastPublish = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = extractor.sampleSize
            if (size < 0) break
            if (size > MAX_SAMPLE_BYTES) throw IOException("字幕样本过大：$size 字节")
            if (size > buffer.capacity()) buffer = ByteBuffer.allocate(size.toInt())
            buffer.clear()
            val read = extractor.readSampleData(buffer, 0)
            if (read < 0) break
            val timeUs = extractor.sampleTime
            if (timeUs / 1000 > endMs) break
            val bytes = ByteArray(read)
            buffer.position(0)
            buffer.get(bytes)
            if (assParser != null) {
                cues += assParser.parse(bytes, timeUs)
            } else {
                // 按 MIME 解码；失败就报错，绝不把二进制退回文本嗅探。
                val timing = decoder.decode(timeUs, bytes, 0, bytes.size)
                val startUs = timing.startTimeUs.takeIf { it != C.TIME_UNSET } ?: timeUs
                val startMs = startUs / 1000
                val endMs = timing.endTimeUs.takeIf { it != C.TIME_UNSET && it > startUs }
                    ?.div(1000) ?: (startMs + 3000L)
                timing.cues.forEach { cue ->
                    if (cue.bitmap != null) return@forEach
                    val text = cue.text?.toString().orEmpty()
                    if (text.isNotBlank()) cues += SubtitleCue(startMs, endMs, text)
                }
            }
            val now = System.nanoTime()
            if (cues.isNotEmpty() && (lastPublish == 0L || now - lastPublish >= 500_000_000L)) {
                onProgress(cues.sortedAndDistinct())
                lastPublish = now
            }
            if (!extractor.advance()) break
        }
        cues.sortedAndDistinct()
    }

    private suspend fun <T> withExtractor(
        context: Context,
        sourceFactory: () -> MediaDataSource,
        byteBudget: Long = Long.MAX_VALUE,
        block: suspend (MediaExtractorCompat) -> T
    ): T {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        val extractor = MediaExtractorCompat(extractorsFactory(), DefaultDataSource.Factory(context))
        var source: MediaDataSource? = null
        var sourceFailure: Exception? = null
        var bytesRead = 0L
        try {
            source = sourceFactory()
            val opened = source
            // advance() 可能跳过大量无字幕的媒体包，底层读取处也检查取消。
            extractor.setDataSource(object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    try {
                        coroutine.ensureActive()
                        if (bytesRead > byteBudget - size) throw IOException("字幕读取超过本次流量上限")
                        return opened.readAt(position, buffer, offset, size).also { if (it > 0) bytesRead += it }
                    } catch (error: Exception) {
                        sourceFailure = error
                        throw error
                    }
                }
                override fun getSize(): Long = try {
                    opened.size
                } catch (error: Exception) {
                    sourceFailure = error
                    throw error
                }
                override fun close() = Unit
            })
            val result = block(extractor)
            // MediaExtractorCompat treats read exceptions as EOF internally.
            // A disconnected SMB stream must not become a successfully cached partial track.
            coroutine.ensureActive()
            sourceFailure?.let { throw it }
            return result
        } finally {
            runCatching { extractor.release() }
            runCatching { source?.close() }.onFailure { Log.w("DualSubTV", "关闭字幕读取源失败", it) }
        }
    }
}
