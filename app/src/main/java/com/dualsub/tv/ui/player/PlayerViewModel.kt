package com.dualsub.tv.ui.player

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.EmbeddedSubtitleReader
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.player.PlayerController
import com.dualsub.tv.player.SubtitleSource
import com.dualsub.tv.player.SubtitleStyle
import com.dualsub.tv.player.SubtitleTrack
import com.dualsub.tv.subtitle.SubtitleCue
import com.dualsub.tv.subtitle.SubtitleParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 播放页的状态持有者。
 *
 * 双字幕的实现要点：主、次两路各自持有一个 [SubtitleTrack]（来源、字幕条目、时间偏移、样式），
 * 由同一条 ticker 每 [TICK_INTERVAL_MS] 毫秒根据 `player.currentPosition` 各自定位当前字幕。
 * 两路互不影响 —— 可以一路内嵌、一路外挂，也可以各自调样式与时间偏移。
 *
 * 状态分三类，不要混用：
 * - [playerError]：**致命**错误，覆盖整屏显示；
 * - [notice]：非致命提示（例如「音频不受支持，已静音播放」），小字提示；
 * - [mediaInfo]：解出来的音视频编码，仅供诊断层查看。
 */
class PlayerViewModel(
    private val appContext: Context,
    private val services: AppServices,
    val video: VideoItem
) : ViewModel() {

    private val settings = services.settings
    private val controller = PlayerController(appContext, services.dataSourceFactory)
    val player: Player = controller.player

    private val _primary = MutableStateFlow(SubtitleTrack(style = SubtitleStyle.PRIMARY))
    val primary: StateFlow<SubtitleTrack> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow(SubtitleTrack(style = SubtitleStyle.SECONDARY))
    val secondary: StateFlow<SubtitleTrack> = _secondary.asStateFlow()

    private val _primaryText = MutableStateFlow<String?>(null)
    val primaryText: StateFlow<String?> = _primaryText.asStateFlow()

    private val _secondaryText = MutableStateFlow<String?>(null)
    val secondaryText: StateFlow<String?> = _secondaryText.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(video.durationMs)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** 播放失败的原因；非空时播放页会覆盖显示出来。 */
    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    /** 非致命提示，显示在画面下方而不遮挡内容。 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    /** 已选中的音视频编码信息，供诊断层显示。 */
    private val _mediaInfo = MutableStateFlow<String?>(null)
    val mediaInfo: StateFlow<String?> = _mediaInfo.asStateFlow()

    private val _embeddedTracks = MutableStateFlow<List<EmbeddedSubtitleReader.TrackInfo>>(emptyList())
    val embeddedTracks: StateFlow<List<EmbeddedSubtitleReader.TrackInfo>> = _embeddedTracks.asStateFlow()

    private var tickerJob: Job? = null
    private var released = false

    /** 音频轨容错只尝试一次，避免失败时无限重试。 */
    private var audioFallbackTried = false

    init {
        observePlayer()
        restoreAndOpen()
        startTicker()
        loadEmbeddedTrackList()
    }

    // ---------------------------------------------------------------- 播放控制

    fun togglePlayPause() {
        if (released) return
        if (controller.player.isPlaying) controller.player.pause() else controller.player.play()
    }

    fun seekBy(deltaMs: Long) {
        if (released) return
        val target = controller.player.currentPosition + deltaMs
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val upperBound = _durationMs.value.takeIf { it > 0L } ?: Long.MAX_VALUE
        controller.player.seekTo(positionMs.coerceIn(0L, upperBound))
    }

    fun attachSurface(surfaceView: android.view.SurfaceView) {
        if (released) return
        controller.attachSurface(surfaceView)
    }

    /** 由界面在离开播放页时调用，确保最后一次进度被写入。 */
    fun saveProgressNow() {
        if (released) return
        val position = controller.player.currentPosition
        if (position > 0L) {
            viewModelScope.launch { settings.setLastPositionMs(video.storageKey, position) }
        }
    }

    /**
     * 界面离开播放页（或切换到另一个视频）时调用：停掉刷新循环并释放播放器。
     *
     * 必须显式调用 —— [com.dualsub.tv.ui.AppRoot] 不再把本 VM 放进 Activity 级的
     * ViewModelStore，`onCleared()` 不保证及时触发，播放器会继续在后台读网络、继续出声。
     */
    fun release() {
        if (released) return
        released = true
        tickerJob?.cancel()
        tickerJob = null
        runCatching { controller.player.stop() }
        runCatching { controller.player.clearMediaItems() }
        controller.release()
    }

    // ---------------------------------------------------------------- 字幕来源

    fun selectNone(forPrimary: Boolean) = setSource(SubtitleSource.None, forPrimary)

    fun selectEmbedded(track: EmbeddedSubtitleReader.TrackInfo, forPrimary: Boolean) {
        setSource(
            SubtitleSource.EmbeddedTrack(
                trackIndex = track.index,
                mimeType = track.mimeType,
                language = track.language,
                label = track.label
            ),
            forPrimary
        )
    }

    fun selectExternalFile(uri: Uri, displayName: String, forPrimary: Boolean) {
        setSource(SubtitleSource.ExternalFile(uri.toString(), displayName), forPrimary)
    }

    private fun setSource(source: SubtitleSource, forPrimary: Boolean) {
        viewModelScope.launch {
            if (forPrimary) {
                settings.setPrimarySource(video.storageKey, source)
            } else {
                settings.setSecondarySource(video.storageKey, source)
            }
        }
        applySource(source, forPrimary)
    }

    private fun applySource(source: SubtitleSource, forPrimary: Boolean) {
        updateTrack(forPrimary) {
            it.copy(source = source, cues = emptyList(), error = null, isLoading = source !is SubtitleSource.None)
        }
        when (source) {
            SubtitleSource.None -> updateTrack(forPrimary) { it.copy(isLoading = false) }
            is SubtitleSource.ExternalFile -> loadExternal(source, forPrimary)
            is SubtitleSource.EmbeddedTrack -> loadEmbedded(source, forPrimary)
        }
    }

    private fun loadExternal(source: SubtitleSource.ExternalFile, forPrimary: Boolean) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = appContext.contentResolver
                        .openInputStream(Uri.parse(source.uri))
                        ?.use { it.readBytes() }
                        ?: ByteArray(0)
                    SubtitleParsers.parseByFileName(bytes, source.displayName)
                }
            }
            outcome
                .onSuccess { cues -> deliver(cues, forPrimary) }
                .onFailure { error ->
                    updateTrack(forPrimary) {
                        it.copy(cues = emptyList(), isLoading = false, error = "读取字幕失败：${error.message}")
                    }
                }
        }
    }

    private fun loadEmbedded(source: SubtitleSource.EmbeddedTrack, forPrimary: Boolean) {
        viewModelScope.launch {
            val cues = withContext(Dispatchers.IO) {
                EmbeddedSubtitleReader.readCues(
                    sourceFactory = ::openVideoDataSource,
                    trackIndex = source.trackIndex
                )
            }
            deliver(cues, forPrimary)
        }
    }

    private fun deliver(cues: List<SubtitleCue>, forPrimary: Boolean) {
        updateTrack(forPrimary) {
            it.copy(
                cues = cues,
                isLoading = false,
                error = if (cues.isEmpty()) "该来源没有解析出字幕条目" else null
            )
        }
    }

    private fun loadEmbeddedTrackList() {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                EmbeddedSubtitleReader.listTracks(::openVideoDataSource)
            }
            _embeddedTracks.value = tracks
        }
    }

    /** 按当前片源的 scheme 造一个随机读数据源。 */
    private fun openVideoDataSource(): MediaDataSource =
        services.mediaSources.create(video.uri)
            ?: throw IOException("当前片源不支持读取内嵌字幕（可改用外挂字幕文件）")

    // ---------------------------------------------------------------- 样式与偏移

    fun changeStyle(forPrimary: Boolean, transform: (SubtitleStyle) -> SubtitleStyle) {
        val current = if (forPrimary) _primary.value.style else _secondary.value.style
        val updated = transform(current)
        updateTrack(forPrimary) { it.copy(style = updated) }
        viewModelScope.launch {
            if (forPrimary) settings.setPrimaryStyle(updated) else settings.setSecondaryStyle(updated)
        }
    }

    fun adjustOffset(forPrimary: Boolean, deltaMs: Long) {
        updateTrack(forPrimary) { it.copy(offsetMs = it.offsetMs + deltaMs) }
    }

    // ---------------------------------------------------------------- 内部

    private fun updateTrack(forPrimary: Boolean, transform: (SubtitleTrack) -> SubtitleTrack) {
        if (forPrimary) {
            _primary.value = transform(_primary.value)
        } else {
            _secondary.value = transform(_secondary.value)
        }
    }

    private fun observePlayer() {
        controller.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val duration = controller.player.duration
                    if (duration > 0L) _durationMs.value = duration
                    // 能走到 READY 说明这一路的读取是通的，清掉旧错误
                    _playerError.value = null
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // 在拿到轨道信息时就记录下来：即使之后解码失败，这段信息也有助于判断
                _mediaInfo.value = describeSelectedTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                _isBuffering.value = false

                // 不少 MKV 带 DTS / TrueHD 音轨，ExoPlayer 会先尝试 FFmpeg 软解；
                // 若 FFmpeg 扩展也解不了才会走到这里。退一步：关掉音频轨重试，
                // 至少画面能放出来，并如实告诉用户没声音的原因。
                if (!audioFallbackTried && isDecodingError(error.errorCode)) {
                    audioFallbackTried = true
                    _playerError.value = null
                    _notice.value = "音频编码不支持（已尝试软解），改为静音播放"
                    Log.i(TAG, "解码失败，禁用音频轨后重试：${error.errorCodeName}")
                    runCatching { controller.disableAudioAndRetry() }
                    return
                }

                _playerError.value = buildString {
                    append("播放失败：").append(error.errorCodeName)
                    val detail = error.cause?.message ?: error.message
                    detail?.takeIf { it.isNotBlank() }?.let { append("（").append(it).append("）") }
                }
            }
        })
    }

    /** 与解码相关的错误码；只有这些才值得尝试「关掉音频再试」。 */
    private fun isDecodingError(code: Int): Boolean = when (code) {
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> true

        else -> false
    }

    /** 把当前选中的音视频轨道拼成一行可读文本，用于诊断「为什么这个文件播不了」。 */
    private fun describeSelectedTracks(tracks: Tracks): String {
        val parts = ArrayList<String>()
        for (group in tracks.groups) {
            val kind = when (group.type) {
                C.TRACK_TYPE_VIDEO -> "视频"
                C.TRACK_TYPE_AUDIO -> "音频"
                C.TRACK_TYPE_TEXT -> "字幕"
                else -> "其他"
            }
            for (index in 0 until group.length) {
                if (!group.isTrackSelected(index)) continue
                val format = group.getTrackFormat(index)
                parts += buildString {
                    append(kind).append('=')
                    append(format.sampleMimeType ?: format.containerMimeType ?: "未知")
                    if (group.type == C.TRACK_TYPE_VIDEO && format.width > 0 && format.height > 0) {
                        append(" ").append(format.width).append('x').append(format.height)
                    }
                    format.codecs?.takeIf { it.isNotBlank() }?.let { append(" [").append(it).append(']') }
                }
            }
        }
        return if (parts.isEmpty()) "尚未取得轨道信息" else parts.joinToString("；")
    }

    private fun restoreAndOpen() {
        viewModelScope.launch {
            val startPosition = settings.lastPositionMs(video.storageKey).first()
            val primaryStyle = settings.primaryStyle.first()
            val secondaryStyle = settings.secondaryStyle.first()
            val primarySource = settings.primarySource(video.storageKey).first()
            val secondarySource = settings.secondarySource(video.storageKey).first()

            _primary.value = _primary.value.copy(style = primaryStyle)
            _secondary.value = _secondary.value.copy(style = secondaryStyle)

            if (released) return@launch
            controller.open(video.uri, startPosition)

            applySource(primarySource, forPrimary = true)
            applySource(secondarySource, forPrimary = false)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var sinceLastSave = 0L
            while (isActive) {
                if (released) break
                val position = controller.player.currentPosition
                _positionMs.value = position
                _primaryText.value = _primary.value.cueAt(position)?.text
                _secondaryText.value = _secondary.value.cueAt(position)?.text

                sinceLastSave += TICK_INTERVAL_MS
                if (sinceLastSave >= SAVE_INTERVAL_MS) {
                    sinceLastSave = 0L
                    if (position > 0L) settings.setLastPositionMs(video.storageKey, position)
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    class Factory(
        private val appContext: Context,
        private val services: AppServices,
        private val video: VideoItem
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(appContext, services, video) as T
    }

    private companion object {
        /** 字幕定位刷新间隔：50ms 约等于 20 次/秒，肉眼足够顺滑且几乎不占 CPU。 */
        const val TICK_INTERVAL_MS = 50L

        /** 断点落盘间隔。 */
        const val SAVE_INTERVAL_MS = 5000L

        const val TAG = "DualSubTV"
    }
}
