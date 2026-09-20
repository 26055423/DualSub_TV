package com.dualsub.tv.player

import com.dualsub.tv.media.SubtitleWindow
import com.dualsub.tv.subtitle.SubtitleCue
import com.dualsub.tv.subtitle.sortedAndDistinct
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class EmbeddedCueState(
    val tracks: Set<Int> = emptySet(),
    val cues: Map<Int, List<SubtitleCue>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/** UI-confined window scheduler: one cancellable read for both selected tracks. */
internal class EmbeddedCueCache(
    private val scope: CoroutineScope,
    private val read: suspend (Set<Int>, Long, Long, (SubtitleWindow) -> Unit) -> SubtitleWindow,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class Request(val tracks: Set<Int>, val start: Long, val end: Long) {
        fun covers(tracks: Set<Int>, position: Long) = this.tracks == tracks && position in start until end - 15_000
    }
    private val _state = MutableStateFlow(EmbeddedCueState())
    val state = _state.asStateFlow()
    private val serial = Mutex()
    private data class Range(val start: Long, val end: Long)
    private data class Chunk(val track: Int, val range: Range, val cues: List<SubtitleCue>, val weight: Long)
    private val cache = ArrayList<Chunk>()
    private var active: Request? = null
    private var loaded: Request? = null
    private var failed: Request? = null
    private var retryAt = 0L
    private var generation = 0
    private var job: Job? = null

    private fun missing(track: Int, range: Range): List<Range> {
        val gaps = ArrayList<Range>()
        var cursor = range.start
        for (chunk in cache.filter { it.track == track }.sortedBy { it.range.start }) {
            if (chunk.range.end <= cursor) continue
            if (chunk.range.start >= range.end) break
            if (chunk.range.start > cursor) gaps += Range(cursor, chunk.range.start)
            cursor = maxOf(cursor, chunk.range.end)
        }
        if (cursor < range.end) gaps += Range(cursor, range.end)
        return gaps
    }

    private fun collect(request: Request): Map<Int, List<SubtitleCue>> = request.tracks.associateWith { track ->
        cache.filter { it.track == track && it.range.end >= request.start && it.range.start <= request.end }
            .flatMap { it.cues }.filter { it.endMs >= request.start && it.startMs <= request.end }.sortedAndDistinct()
    }

    private fun merge(request: Request, base: Map<Int, List<SubtitleCue>>, more: Map<Int, List<SubtitleCue>>) =
        request.tracks.associateWith { track ->
            (base[track].orEmpty() + more[track].orEmpty())
                .filter { it.endMs >= request.start && it.startMs <= request.end }.sortedAndDistinct()
        }

    private fun remember(tracks: Set<Int>, range: Range, window: SubtitleWindow) {
        for (track in tracks) {
            val cues = window.cues[track].orEmpty()
            // Include ASS collections in the estimate; never let a long session retain the whole film.
            val weight = cues.sumOf { cue ->
                128L + cue.text.length * 2L + (cue.assOverride?.let { ass ->
                    256L + (ass.spans.size + ass.transforms.size + ass.fadeParts.size) * 96L +
                        ass.karaokeSegments.sumOf { 64L + it.text.length * 2L }
                } ?: 0L)
            }
            if (weight > 4L * 1024 * 1024) continue
            cache += Chunk(track, range, cues, weight)
        }
        while (cache.size > 16 || cache.sumOf { it.weight } > 4L * 1024 * 1024) cache.removeAt(0)
    }

    fun clear() {
        request(emptySet(), 0)
        cache.clear()
    }

    fun request(tracks: Set<Int>, positionMs: Long) {
        val position = positionMs.coerceAtLeast(0)
        if (tracks.isEmpty()) {
            generation++
            job?.cancel()
            job = null
            active = null
            loaded = null
            failed = null
            _state.value = EmbeddedCueState()
            return
        }
        if (loaded?.covers(tracks, position) == true) return
        if (active?.covers(tracks, position) == true && job?.isActive == true) return
        if (failed?.covers(tracks, position) == true && nowMs() < retryAt) return
        val bucket = position / 30_000 * 30_000
        val request = Request(tracks.toSet(), (bucket - 30_000).coerceAtLeast(0), bucket + 60_000)
        generation++
        val version = generation
        job?.cancel()
        active = request
        loaded = null
        // Track-local coverage lets a new secondary track reuse the current primary.
        // Equal gaps are grouped so both slots still share a single MKV pass.
        val pending = linkedMapOf<Range, MutableSet<Int>>()
        for (track in request.tracks) for (gap in missing(track, Range(request.start, request.end))) {
            pending.getOrPut(gap) { linkedSetOf() } += track
        }
        var collected = collect(request)
        // Move the actual entries by index, preserving their order and identity.
        val used = ArrayList<Chunk>()
        for (index in cache.lastIndex downTo 0) {
            val chunk = cache[index]
            if (chunk.track in tracks && chunk.range.end >= request.start && chunk.range.start <= request.end) {
                used.add(cache.removeAt(index))
            }
        }
        cache.addAll(used.asReversed())
        if (pending.isEmpty()) {
            active = null
            loaded = request
            failed = null
            _state.value = EmbeddedCueState(tracks, collected)
            return
        }
        _state.value = EmbeddedCueState(tracks, collected, isLoading = true)
        job = scope.launch {
            try {
                var activePart = 0
                for ((range, neededTracks) in pending) {
                    val part = ++activePart
                    val base = collected
                    // cancel() is cooperative; wait until the old network read actually exits.
                    val window = serial.withLock {
                        read(neededTracks, range.start, range.end) { partial ->
                            scope.launch {
                                if (version == generation && active == request && activePart == part) {
                                    _state.value = _state.value.copy(cues = merge(request, base, partial.cues), isLoading = true)
                                }
                            }
                        }
                    }
                    activePart++ // Ignore queued progress from this completed part.
                    if (version != generation) return@launch
                    remember(neededTracks, range, window)
                    collected = merge(request, collected, window.cues)
                    _state.value = EmbeddedCueState(tracks, collected, isLoading = true)
                }
                loaded = request
                active = null
                failed = null
                _state.value = EmbeddedCueState(tracks, collected)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (version != generation) return@launch
                active = null
                failed = request
                retryAt = nowMs() + 10_000
                _state.value = _state.value.copy(isLoading = false, error =
                    if (error is TimeoutCancellationException) "读取此处字幕超时，请检查网络或重试"
                    else error.message ?: error.javaClass.simpleName)
            }
        }
    }
}
