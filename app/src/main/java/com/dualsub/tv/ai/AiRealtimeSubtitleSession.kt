package com.dualsub.tv.ai

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.MediaExtractorCompat
import com.dualsub.tv.media.EmbeddedSubtitleReader
import com.dualsub.tv.network.MediaSourceProvider
import com.dualsub.tv.subtitle.SubtitleCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.math.abs

/**
 * 实时 AI 字幕流会话。
 *
 * 播放器启动后以滑动窗口方式持续提取前方音频，每 20s 一段串行调用 API，
 * 结果立即以 [onCues] 推送到次字幕层。不保存完整 SRT 文件。
 *
 * 生命周期由 PlayerViewModel 管理：[start] 启动循环，[stop] 取消 scope，
 * [updatePosition] 由 ticker（50ms）频繁调用，仅写 @Volatile 变量，无锁无开销。
 */
class AiRealtimeSubtitleSession(
    private val context: Context,
    private val generator: AiSubtitleGenerator,
    private val mediaSources: MediaSourceProvider,
    private val videoUri: Uri,
    private val config: AiSubtitleConfig,
    private val durationMs: Long,
    private val onCues: (List<SubtitleCue>) -> Unit,
    private val onClear: () -> Unit,
    private val onState: (AiSubtitleState) -> Unit
) {
    @Volatile private var playPositionMs: Long = 0L
    @Volatile private var lastPositionForSeekDetect: Long = 0L

    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(initialPositionMs: Long) {
        playPositionMs = initialPositionMs
        lastPositionForSeekDetect = initialPositionMs
        sessionScope.launch { runLoop(initialPositionMs) }
    }

    fun updatePosition(positionMs: Long) {
        playPositionMs = positionMs
    }

    fun stop() {
        sessionScope.cancel()
    }

    private suspend fun runLoop(startPositionMs: Long) {
        val windowMs = config.liveWindowSec * 1000L
        val leadMs   = config.liveLeadSec   * 1000L
        var extractCursor = startPositionMs
        var consecutiveFailures = 0
        val recentCueTexts = ArrayDeque<String>()

        while (currentCoroutineContext().isActive) {
            currentCoroutineContext().ensureActive()

            // Seek 检测：播放位置相比上次记录跳跃超过阈值，重置提取游标
            val currentPlay = playPositionMs
            if (abs(currentPlay - lastPositionForSeekDetect) > SEEK_RESET_THRESHOLD_MS) {
                extractCursor = currentPlay
                consecutiveFailures = 0
                recentCueTexts.clear()
                onClear()
                onState(AiSubtitleState.Live(currentPlay, "检测到跳转，从 ${currentPlay / 1000}s 重新缓冲…"))
            }
            lastPositionForSeekDetect = currentPlay

            // 已提取到片尾
            if (extractCursor >= durationMs) {
                onState(AiSubtitleState.Live(durationMs, "字幕已生成到片尾"))
                break
            }

            // 超前量已足够，等播放追上再继续提取
            if (extractCursor - currentPlay >= leadMs) {
                delay(WAIT_INTERVAL_MS)
                continue
            }

            val segStart = (extractCursor - OVERLAP_MS).coerceAtLeast(0L)
            val segEnd = (extractCursor + windowMs).coerceAtMost(durationMs)
            val tmpFile = File(context.cacheDir, "ai_live_$segStart.aac")

            onState(AiSubtitleState.Live(extractCursor, "正在提取 ${extractCursor / 1000}s 处音频…"))

            try {
                withTimeout(API_TIMEOUT_MS) {
                    extractAudioWindow(segStart, segEnd, tmpFile)
                    val srt = generator.callQwenOmniFlash(
                        AiSubtitleGenerator.Segment(tmpFile, segStart, segEnd),
                        config,
                        contextLines = recentCueTexts.toList()
                    )
                    val rawCues = generator.parseSrtCues(srt, segStart)
                    // 过滤重叠区（segStart < extractCursor）已推过的 cue，避免重复
                    val newCues = rawCues
                        .filter { (startMs, _, _) -> startMs >= extractCursor }
                        .map { (startMs, endMs, text) -> SubtitleCue(startMs, endMs, text) }
                    if (newCues.isNotEmpty()) {
                        onCues(newCues)
                        // 把新 cue 的译文压入队列，保留最近 CONTEXT_CUE_COUNT 条
                        newCues.forEach { recentCueTexts.addLast(it.text) }
                        while (recentCueTexts.size > CONTEXT_CUE_COUNT) recentCueTexts.removeFirst()
                    }
                    consecutiveFailures = 0
                    val buffered = extractCursor + windowMs
                    onState(AiSubtitleState.Live(buffered, "已缓冲到 ${buffered / 1000}s"))
                }
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) break
                consecutiveFailures++
                if (consecutiveFailures >= MAX_FAILURES) {
                    onState(AiSubtitleState.LiveFailed(
                        reason = "连续 $MAX_FAILURES 次失败：${e.message?.take(40) ?: "超时或网络错误"}",
                        canFallback = true
                    ))
                    break
                }
                // 跳过这段继续下一窗口
                onState(AiSubtitleState.Live(extractCursor, "第 $consecutiveFailures 次失败，跳过继续…"))
            } finally {
                runCatching { tmpFile.delete() }
            }

            extractCursor += windowMs
        }
    }

    private fun extractAudioWindow(segStartMs: Long, segEndMs: Long, outFile: File) {
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
                extractor.selectTrack(audioTrack)
                generator.writeAudioSegment(extractor, outFile, segStartMs, segEndMs)
            } finally {
                runCatching { extractor.release() }
            }
        }
    }

    companion object {
        private const val OVERLAP_MS = 3_000L
        private const val MAX_FAILURES = 2
        private const val API_TIMEOUT_MS = 90_000L
        private const val SEEK_RESET_THRESHOLD_MS = 60_000L
        private const val WAIT_INTERVAL_MS = 500L
        private const val CONTEXT_CUE_COUNT = 12
    }
}
