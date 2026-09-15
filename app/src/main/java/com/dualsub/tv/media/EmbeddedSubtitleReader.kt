package com.dualsub.tv.media

import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import com.dualsub.tv.subtitle.SubtitleCue
import com.dualsub.tv.subtitle.SubtitleFormat
import com.dualsub.tv.subtitle.SubtitleParsers
import com.dualsub.tv.subtitle.SubtitleTextCleaner
import com.dualsub.tv.subtitle.SubtitleTextDecoder
import com.dualsub.tv.subtitle.sortedAndDistinct
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * 视频内嵌字幕轨的枚举与抽取。
 *
 * 用 Android 框架自带的 [MediaExtractor] 实现，不引入任何额外依赖，
 * 也不依赖 ExoPlayer 的字幕渲染管线（后者已被 [com.dualsub.tv.player.PlayerController] 关闭）。
 *
 * 数据源通过 [MediaDataSource] 注入，因此本地文件（[LocalMediaDataSource]）与
 * SMB 上的文件（[com.dualsub.tv.network.smb.SmbMediaDataSource]）走的是同一套逻辑。
 *
 * 不同容器的字幕样本形态差别很大，这里逐一兜住：
 * - MP4/MKV 里存放的是**完整 SRT/ASS 文档**（样本自带时间戳）→ 直接交给解析器；
 * - MKV 里存放的是**逐条纯文本**（时间在样本的时间戳上）→ 用相邻样本的时间戳构造区间；
 * - 空样本用于「清空字幕」→ 跳过，不产生 cue。
 *
 * 已知不支持：MP4 的 `tx3g` 二进制字幕（可读轨道但不会解析出文本）。
 */
object EmbeddedSubtitleReader {

    /** 单条样本上限。字幕样本通常只有几百字节，1 MiB 足够宽裕。 */
    private const val SAMPLE_BUFFER_SIZE = 1 shl 20

    /** 纯文本样本没有自带结束时间时的默认显示时长。 */
    private const val DEFAULT_CUE_DURATION_MS = 3000L

    /**
     * 轨道标题的 MediaFormat key。
     * 框架没有公开 `KEY_TITLE` 常量，但容器里确实会写这个字段，因此用字面量读取。
     */
    private const val KEY_TRACK_TITLE = "title"

    data class TrackInfo(
        val index: Int,
        val mimeType: String?,
        val language: String?,
        val title: String?,
        val format: SubtitleFormat
    ) {
        val label: String
            get() = buildString {
                append(format.displayName)
                language?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                title?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                append("（内嵌 #").append(index).append('）')
            }
    }

    /** 列出视频里所有可用的字幕轨。[sourceFactory] 每次调用需返回一个**新的**数据源。 */
    fun listTracks(sourceFactory: () -> MediaDataSource): List<TrackInfo> =
        withExtractor(sourceFactory) { extractor ->
            (0 until extractor.trackCount).mapNotNull { index ->
                val format = runCatching { extractor.getTrackFormat(index) }.getOrNull()
                    ?: return@mapNotNull null
                val mime = runCatching { format.getString(MediaFormat.KEY_MIME) }.getOrNull()
                val subtitleFormat = SubtitleFormat.fromMimeType(mime)
                if (subtitleFormat == SubtitleFormat.UNKNOWN) return@mapNotNull null
                TrackInfo(
                    index = index,
                    mimeType = mime,
                    language = runCatching { format.getString(MediaFormat.KEY_LANGUAGE) }.getOrNull(),
                    title = runCatching { format.getString(KEY_TRACK_TITLE) }.getOrNull(),
                    format = subtitleFormat
                )
            }
        } ?: emptyList()

    /** 抽取指定字幕轨的全部字幕。 */
    fun readCues(sourceFactory: () -> MediaDataSource, trackIndex: Int): List<SubtitleCue> =
        withExtractor(sourceFactory) { extractor ->
            runCatching {
                extractor.selectTrack(trackIndex)
                decodeSamples(readSamples(extractor))
            }.getOrDefault(emptyList())
        } ?: emptyList()

    private fun <T> withExtractor(
        sourceFactory: () -> MediaDataSource,
        block: (MediaExtractor) -> T
    ): T? {
        val extractor = MediaExtractor()
        var source: MediaDataSource? = null
        return try {
            source = sourceFactory()
            extractor.setDataSource(source)
            block(extractor)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
            source?.let { dataSource -> runCatching { dataSource.close() } }
        }
    }

    private data class Sample(val timeUs: Long, val bytes: ByteArray)

    private fun readSamples(extractor: MediaExtractor): List<Sample> {
        val samples = ArrayList<Sample>()
        val buffer = ByteBuffer.allocate(SAMPLE_BUFFER_SIZE)
        while (true) {
            val size = runCatching { extractor.readSampleData(buffer, 0) }.getOrNull() ?: break
            if (size < 0) break

            val timeUs = extractor.sampleTime
            val bytes = ByteArray(size)
            buffer.position(0)
            buffer.limit(size)
            buffer.get(bytes)
            buffer.clear()

            samples += Sample(timeUs, bytes)
            if (!runCatching { extractor.advance() }.getOrDefault(false)) break
        }
        return samples
    }

    private fun decodeSamples(samples: List<Sample>): List<SubtitleCue> {
        if (samples.isEmpty()) return emptyList()

        // 情形一：整条轨拼起来就是一份完整字幕文档
        val joined = ByteArrayOutputStream().apply {
            samples.forEach { write(it.bytes) }
        }.toByteArray()
        val wholeDocument = SubtitleParsers.parseByFileName(joined, null)
        if (wholeDocument.isNotEmpty()) return wholeDocument

        // 情形二：逐个样本是纯文本，时间取样本时间戳
        val cues = ArrayList<SubtitleCue>(samples.size)
        samples.forEachIndexed { index, sample ->
            val parsed = SubtitleParsers.parseByFileName(sample.bytes, null)
            if (parsed.isNotEmpty()) {
                cues += parsed
                return@forEachIndexed
            }

            val text = plainText(sample.bytes)
            if (text.isEmpty()) return@forEachIndexed

            val startMs = sample.timeUs / 1000
            val nextStartMs = samples.getOrNull(index + 1)?.timeUs?.div(1000)
            val endMs = nextStartMs?.takeIf { it > startMs } ?: (startMs + DEFAULT_CUE_DURATION_MS)
            cues += SubtitleCue(startMs, endMs, text)
        }
        return cues.sortedAndDistinct()
    }

    private fun plainText(bytes: ByteArray): String = SubtitleTextCleaner.tidy(
        SubtitleTextCleaner.stripAssOverrides(
            SubtitleTextCleaner.stripHtmlTags(
                SubtitleTextDecoder.decode(bytes)
            )
        )
    )
}
