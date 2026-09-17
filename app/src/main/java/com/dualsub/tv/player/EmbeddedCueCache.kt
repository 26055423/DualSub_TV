package com.dualsub.tv.player

import com.dualsub.tv.media.SubtitleWindow
import com.dualsub.tv.subtitle.SubtitleCue
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
    private val cache = linkedMapOf<Request, SubtitleWindow>()
    private var active: Request? = null
    private var loaded: Request? = null
    private var failed: Request? = null
    private var retryAt = 0L
    private var generation = 0
    private var job: Job? = null

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
        val keepCues = loaded?.let { it.tracks == tracks && position in it.start until it.end } == true
        generation++
        val version = generation
        job?.cancel()
        active = request
        loaded = null
        cache.entries.lastOrNull { it.key.covers(tracks, position) }?.let { (key, window) ->
            active = null
            loaded = key
            _state.value = EmbeddedCueState(tracks, window.cues)
            return
        }
        _state.value = EmbeddedCueState(tracks, if (keepCues) _state.value.cues else emptyMap(), isLoading = true)
        job = scope.launch {
            try {
                // cancel() is cooperative; never start the next network scan until the old one exits.
                val window = serial.withLock {
                    read(request.tracks, request.start, request.end) { partial ->
                        scope.launch {
                            if (version == generation && active == request) {
                                _state.value = _state.value.copy(cues = partial.cues, isLoading = true)
                            }
                        }
                    }
                }
                if (version != generation) return@launch
                cache[request] = window
                while (cache.size > 4) cache.remove(cache.keys.first())
                loaded = request
                active = null
                failed = null
                _state.value = EmbeddedCueState(tracks, window.cues)
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
