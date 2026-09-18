package com.dualsub.tv.ai

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaFormat
import android.net.Uri
import android.util.Base64
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.MediaExtractorCompat
import com.dualsub.tv.media.EmbeddedSubtitleReader
import com.dualsub.tv.network.MediaSourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class AiSubtitleGenerator(
    private val context: Context,
    private val mediaSources: MediaSourceProvider
) {

    data class Segment(
        val file: File,
        val startMs: Long,
        val endMs: Long
    )

    internal val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        videoUri: Uri,
        config: AiSubtitleConfig,
        onProgress: (current: Int, total: Int, phase: String) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = abs(videoUri.toString().hashCode()).toString(16)
            val outDir = File(context.filesDir, "ai_subtitles").also { it.mkdirs() }
            val outFile = File(outDir, "$hash.srt")

            val cacheDir = File(context.cacheDir, "ai_audio/$hash").also { it.mkdirs() }

            onProgress(0, 1, "正在提取音频…")
            val segments = extractAudioSegments(videoUri, cacheDir, onProgress)
            if (segments.isEmpty()) error("未找到可提取的音频轨")

            onProgress(0, segments.size, "正在调用 AI 翻译…")
            val srtParts = callApiConcurrent(segments, config, onProgress)

            onProgress(segments.size, segments.size, "正在合并字幕…")
            val merged = mergeSrt(srtParts)
            outFile.writeText(merged, Charsets.UTF_8)

            cacheDir.deleteRecursively()

            Uri.fromFile(outFile)
        }
    }

    private suspend fun extractAudioSegments(
        videoUri: Uri,
        cacheDir: File,
        onProgress: (Int, Int, String) -> Unit
    ): List<Segment> = withContext(Dispatchers.IO) {
        val source = mediaSources.create(videoUri)
            ?: error("不支持的视频来源：$videoUri")

        source.use { src ->
            val extractor = MediaExtractorCompat(
                EmbeddedSubtitleReader.extractorsFactory(),
                DefaultDataSource.Factory(context)
            )
            try {
                extractor.setDataSource(src)
                val audioTrack = (0 until extractor.trackCount).firstOrNull { idx ->
                    extractor.getTrackFormat(idx)
                        .getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: error("视频中没有音频轨")

                val trackFormat = extractor.getTrackFormat(audioTrack)
                val durationUs = runCatching { trackFormat.getLong(MediaFormat.KEY_DURATION) }
                    .getOrNull()?.takeIf { it > 0 } ?: (60L * 60 * 1_000_000) // fallback 1h

                extractor.selectTrack(audioTrack)

                val segmentDurationMs = SEGMENT_DURATION_S * 1000L
                val overlapMs = SEGMENT_OVERLAP_S * 1000L
                val totalMs = durationUs / 1000
                val segCount = ((totalMs + segmentDurationMs - 1) / segmentDurationMs).toInt()
                    .coerceAtLeast(1)

                val segments = mutableListOf<Segment>()
                var segIndex = 0
                var segStartMs = 0L

                while (segStartMs < totalMs) {
                    currentCoroutineContext().ensureActive()
                    val segEndMs = (segStartMs + segmentDurationMs + overlapMs).coerceAtMost(totalMs)
                    val segFile = File(cacheDir, "seg_${segIndex}.aac")
                    onProgress(segIndex, segCount, "正在提取音频第 ${segIndex + 1}/$segCount 段…")

                    writeAudioSegment(extractor, segFile, segStartMs, segEndMs)
                    segments += Segment(segFile, segStartMs, segEndMs)

                    segStartMs += segmentDurationMs
                    segIndex++
                    if (segStartMs >= totalMs) break
                }
                segments
            } finally {
                runCatching { extractor.release() }
            }
        }
    }

    internal fun writeAudioSegment(
        extractor: MediaExtractorCompat,
        outFile: File,
        startMs: Long,
        endMs: Long
    ) {
        extractor.seekTo(startMs * 1000, MediaExtractorCompat.SEEK_TO_PREVIOUS_SYNC)
        var buffer = ByteBuffer.allocate(256 * 1024)
        FileOutputStream(outFile).use { fos ->
            while (true) {
                val size = extractor.sampleSize
                if (size < 0) break
                val timeMs = extractor.sampleTime / 1000
                if (timeMs > endMs) break
                if (size > buffer.capacity()) buffer = ByteBuffer.allocate(size.toInt())
                buffer.clear()
                val read = extractor.readSampleData(buffer, 0)
                if (read <= 0) break
                val bytes = ByteArray(read)
                buffer.position(0)
                buffer.get(bytes)
                fos.write(bytes)
                if (!extractor.advance()) break
            }
        }
    }

    private suspend fun callApiConcurrent(
        segments: List<Segment>,
        config: AiSubtitleConfig,
        onProgress: (Int, Int, String) -> Unit
    ): List<Pair<Long, String>> = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT)
        segments.mapIndexed { i, seg ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    currentCoroutineContext().ensureActive()
                    onProgress(i, segments.size, "AI 翻译第 ${i + 1}/${segments.size} 段…")
                    val srt = callQwenOmniFlash(seg, config)
                    onProgress(i + 1, segments.size, "AI 翻译第 ${i + 1}/${segments.size} 段完成")
                    Pair(seg.startMs, srt)
                }
            }
        }.map { it.await() }
    }

    internal fun callQwenOmniFlash(seg: Segment, config: AiSubtitleConfig): String {
        val base64Audio = Base64.encodeToString(seg.file.readBytes(), Base64.NO_WRAP)
        val promptText = buildString {
            append("请转录并翻译为${config.targetLang}，以标准 SRT 格式输出，只输出 SRT 内容，不要加任何解释或标记。")
            if (config.prompt.isNotBlank()) append("\n附加说明：${config.prompt}")
        }
        val body = JSONObject().apply {
            put("model", config.model)
            put("modalities", JSONArray().put("text"))
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "audio_url")
                        put("audio_url", JSONObject().put("url", "data:audio/aac;base64,$base64Audio"))
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", promptText)
                    })
                })
            }))
        }.toString()

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: error("API 返回空响应 (${response.code})")
        if (!response.isSuccessful) error("API 错误 ${response.code}: $responseBody")

        val json = JSONObject(responseBody)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    private fun mergeSrt(parts: List<Pair<Long, String>>): String {
        val sorted = parts.sortedBy { it.first }
        val sb = StringBuilder()
        var cueIndex = 1
        for ((offsetMs, srt) in sorted) {
            parseSrtCues(srt, offsetMs).forEach { (startMs, endMs, text) ->
                sb.append(cueIndex++).append('\n')
                sb.append(formatSrtTime(startMs)).append(" --> ").append(formatSrtTime(endMs)).append('\n')
                sb.append(text.trim()).append('\n')
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    internal fun parseSrtCues(srt: String, offsetMs: Long): List<Triple<Long, Long, String>> {
        val result = mutableListOf<Triple<Long, Long, String>>()
        val blocks = srt.trim().split(Regex("\\n\\s*\\n"))
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 3) continue
            // 找时间戳行（格式 HH:MM:SS,mmm --> HH:MM:SS,mmm）
            val timeLine = lines.firstOrNull { it.contains("-->") } ?: continue
            val timeMatch = TIME_ARROW_REGEX.find(timeLine) ?: continue
            val startMs = parseSrtTimeMs(timeMatch.groupValues[1]) + offsetMs
            val endMs = parseSrtTimeMs(timeMatch.groupValues[2]) + offsetMs
            val textStart = lines.indexOf(timeLine) + 1
            if (textStart >= lines.size) continue
            val text = lines.drop(textStart).joinToString("\n")
            if (text.isNotBlank()) result += Triple(startMs, endMs, text)
        }
        return result
    }

    internal fun parseSrtTimeMs(time: String): Long {
        // HH:MM:SS,mmm
        val parts = time.trim().split(":", ",", ".")
        if (parts.size < 4) return 0L
        return parts[0].toLongOrNull()?.times(3600000L).orZero() +
            parts[1].toLongOrNull()?.times(60000L).orZero() +
            parts[2].toLongOrNull()?.times(1000L).orZero() +
            parts[3].toLongOrNull().orZero()
    }

    private fun formatSrtTime(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val milli = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    private fun Long?.orZero() = this ?: 0L

    companion object {
        private const val SEGMENT_DURATION_S = 300
        private const val SEGMENT_OVERLAP_S = 5
        private const val MAX_CONCURRENT = 6
        private val TIME_ARROW_REGEX = Regex("""(\d{2}:\d{2}:\d{2}[,\.]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,\.]\d{3})""")

        fun cachedSrtUri(context: Context, videoUri: Uri): Uri? {
            val hash = abs(videoUri.toString().hashCode()).toString(16)
            val file = File(context.filesDir, "ai_subtitles/$hash.srt")
            return if (file.exists()) Uri.fromFile(file) else null
        }
    }
}

private suspend fun <T> kotlinx.coroutines.sync.Semaphore.withPermit(block: suspend () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}
