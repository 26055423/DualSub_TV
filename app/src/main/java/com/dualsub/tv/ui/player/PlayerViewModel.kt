package com.dualsub.tv.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.ai.AiRealtimeSubtitleSession
import com.dualsub.tv.ai.AiSubtitleState
import com.dualsub.tv.media.EmbeddedSubtitleReader
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.player.SubtitleSource
import com.dualsub.tv.player.EmbeddedCueCache
import com.dualsub.tv.player.EmbeddedCueState
import com.dualsub.tv.player.SubtitleStyle
import com.dualsub.tv.player.SubtitleTrack
import com.dualsub.tv.player.VlcPlayerController
import com.dualsub.tv.subtitle.SubtitleCue
import com.dualsub.tv.subtitle.SubtitleFormat
import com.dualsub.tv.subtitle.SubtitleParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * 播放失败的结构化说明，供界面直接排版展示。
 *
 * 设计意图：**播不出来可以，但必须让人看懂为什么**。
 * 所以不用一个大字符串，而是拆成「结论 / 原因 / 建议 / 排查详情」四段。
 */
data class PlaybackFailure(
    /** 一句话结论，例如「这个文件打不开」。 */
    val title: String,
    /** 人话原因。 */
    val reason: String,
    /** 针对这个原因的可执行建议；不适用时为 null。 */
    val suggestion: String?,
    /** 原始错误信息、嗅探到的容器名与已解出的轨道，供进一步排查。 */
    val detail: String,
    /** 失败时已经拿到的轨道信息（可能为 null）。 */
    val mediaInfo: String?
)

/**
 * 播放器的只读快照，供界面渲染菜单用。
 *
 * **为什么要有这个**：`audioTrackOptions()` / `videoSizeText()` / `volumePercent()` 这些
 * 都要走 libVLC 的 **JNI**。如果直接在 Compose 的组合过程里调用，每次重组都会跑一遍，
 * 每秒几十次 JNI 足以把 UI 线程压死（表现为「按什么都没反应」）。
 * 所以统一在这里预先算好、用 StateFlow 推给界面，组合期只读内存里的值。
 */
data class PlaybackStats(
    /** 可选音轨：`(id, 带编码的显示名)`。 */
    val audioOptions: List<Pair<Int, String>> = emptyList(),
    /** 当前选中的音轨 id。 */
    val currentAudioId: Int = -1,
    /** 分辨率文本，如 `1920x1080`。 */
    val videoSize: String = "尚未取得",
    /** 当前音量 0-100。 */
    val volume: Int = 0,
    /** 当前倍速。 */
    val rate: Float = 1f,
    /** 码率等流统计文本；尚未起播时为 null。 */
    val streamSummary: String? = null,
    /**
     * 输入速率（**字节/秒**），播放界面右上角的「实时网速」用它。
     *
     * 只有**网络片源**才显示（本地文件由界面侧直接不显示 —— 「网速」对本地读盘没有意义）。
     * 尚未起播、或暂停后不再读盘时为 null。
     */
    val inputBytesPerSec: Long? = null
)

/**
 * 播放页的状态持有者。
 *
 * **播放引擎是 libVLC**（为什么不再是 Media3/ExoPlayer，见 [VlcPlayerController] 的注释）。
 * 播放器在这里只承担两件事：给出播放位置、提供一块视频画布。双字幕完全由
 * `SubtitleOverlay` 两路独立叠加层绘制，所以主/次字幕、ASS 特效、样式与偏移全部原样保留。
 *
 * 状态分三类，不要混用：
 * - [playerError]：**致命**失败，覆盖整屏显示根因与建议；
 * - [notice]：非致命提示（含「已自动选用某条音轨」这类说明），小字提示；
 * - [stats] / [mediaInfo] / [container] / [audioTracks]：只读快照，供菜单与详情显示。
 */
class PlayerViewModel(
    private val appContext: Context,
    private val services: AppServices,
    val video: VideoItem
) : ViewModel() {

    /**
     * 设置存取。**公开**：播放页要用它渲染叠加的「AI 字幕配置」层 —— 播放中也能跳过去改配置。
     */
    val settings = services.settings
    private val controller = VlcPlayerController(
        appContext,
        cachingMs = runBlocking { services.settings.videoCachingMs.first() }
    )
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // AudioFocus：失去焦点时记录是否需要在恢复后继续播放
    private var pausedForAudioFocus = false
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久失去（如另一个应用开始播放）→ 暂停，不自动恢复
                pausedForAudioFocus = false
                if (controller.isPlaying()) controller.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂失去（来电、导航语音）→ 暂停，恢复时续播
                if (controller.isPlaying()) {
                    pausedForAudioFocus = true
                    controller.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedForAudioFocus) {
                    pausedForAudioFocus = false
                    if (!controller.isPlaying()) controller.togglePlayPause()
                }
            }
        }
    }
    private val audioFocusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
    } else null

    private val _primary = MutableStateFlow(SubtitleTrack(style = SubtitleStyle.PRIMARY))
    val primary: StateFlow<SubtitleTrack> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow(SubtitleTrack(style = SubtitleStyle.SECONDARY))
    val secondary: StateFlow<SubtitleTrack> = _secondary.asStateFlow()

    private val _secondaryCue = MutableStateFlow<SubtitleCue?>(null)
    val secondaryCue: StateFlow<SubtitleCue?> = _secondaryCue.asStateFlow()

    /**
     * 主字幕当前该显示的那一条。
     *
     * **为什么主字幕也有 cue 了**：主字幕原先交给 libVLC 的 libass 渲染，但 vlc-android
     * 在 Android 上的字体链路我们够不到（它只读 `/system/etc/fonts.xml` 按族名查，
     * `--freetype-font` 传文件路径无效），在没有中文字体的电视上会整片渲染成方框。
     * 改成和次字幕一样由 Compose 自绘后，字体走**系统字体栈**，方框问题自然消失，
     * 而且两路样式/位置统一，能排版避免上下打架。
     *
     * 例外：**图片字幕**（PGS / VDBSUB / DVBSUB）自绘画不了位图，仍交给 libVLC。
     */
    private val _primaryCue = MutableStateFlow<SubtitleCue?>(null)
    val primaryCue: StateFlow<SubtitleCue?> = _primaryCue.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(video.durationMs)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** 播放失败的结构化原因；非空时播放页覆盖整屏显示。 */
    private val _playerError = MutableStateFlow<PlaybackFailure?>(null)
    val playerError: StateFlow<PlaybackFailure?> = _playerError.asStateFlow()

    /** 非致命提示，显示在画面下方而不遮挡内容。 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    /** 已解出的轨道信息（视频/音频编码），供详情面板显示。 */
    private val _mediaInfo = MutableStateFlow<String?>(null)
    val mediaInfo: StateFlow<String?> = _mediaInfo.asStateFlow()

    /** 音轨概览（有几条、编码、当前选中哪条），用于诊断「有画面没声音」。 */
    private val _audioTracks = MutableStateFlow("尚未取得音轨")
    val audioTracks: StateFlow<String> = _audioTracks.asStateFlow()

    /** 文件头嗅探出的容器名（AVI / MKV / MP4 ...），null 表示识别不出。 */
    private val _container = MutableStateFlow<String?>(null)
    val container: StateFlow<String?> = _container.asStateFlow()

    /** libVLC 的只读快照（音轨选项/当前音轨/分辨率/音量/倍速），组合期只读这个。 */
    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats.asStateFlow()

    private val _aiSubtitleState = MutableStateFlow<AiSubtitleState>(AiSubtitleState.Idle)
    val aiSubtitleState: StateFlow<AiSubtitleState> = _aiSubtitleState.asStateFlow()
    private var aiSubtitleJob: Job? = null
    private var liveSession: AiRealtimeSubtitleSession? = null

    private val _embeddedTracks = MutableStateFlow<List<EmbeddedSubtitleReader.TrackInfo>>(emptyList())
    val embeddedTracks: StateFlow<List<EmbeddedSubtitleReader.TrackInfo>> = _embeddedTracks.asStateFlow()
    private val _embeddedTrackStatus = MutableStateFlow("正在读取轨道…")
    val embeddedTrackStatus = _embeddedTrackStatus.asStateFlow()
    private var tracksReady = false
    private var trackListJob: Job? = null
    private val subtitleIo = Mutex()

    /** Both subtitle slots share cancellable, bounded reads with progressive delivery. */
    private val subtitleScope = CoroutineScope(
        viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job])
    )
    private val embeddedCues = EmbeddedCueCache(subtitleScope, read = { tracks, start, end, progress ->
        withTimeout(20_000) {
            withContext(Dispatchers.IO) {
                subtitleIo.withLock {
                    val window = EmbeddedSubtitleReader.readWindow(appContext, ::openVideoDataSource, tracks, start, end, progress)
                    Log.i(TAG, "字幕窗口 ${start / 1000}-${end / 1000}s，轨=$tracks，" +
                        "读取 ${window.bytesRead / 1024} KiB，${window.cues.values.sumOf { it.size }} 条")
                    window
                }
            }
        }
    })
    private var primaryLoadJob: Job? = null
    private var secondaryLoadJob: Job? = null
    private var seekJob: Job? = null
    private var pendingSeek: Long? = null
    private var pendingSeekDeadline = 0L
    // open() 可能先于 attachViews() 执行；先记住参数，等 SurfaceView 就绪再真正播放
    private var pendingOpenArgs: Triple<Uri, Long, List<String>>? = null
    private var viewAttached = false
    private var sourcesRestored = false
    private var primarySelectionMade = false
    private var secondarySelectionMade = false

    /**
     * 是否需要「打开媒体后自动选一条中文字幕」。
     *
     * 设置里没保存过字幕来源时会置为 true；等内嵌轨道列表出来后再真正去选。
     * 之所以要这个标志，是因为「恢复来源」与「列举轨道」是两条并发的异步流程 ——
     * 轨道列表先到还是来源先恢复是不确定的，用一个标志把两边解耦，避免依赖时序。
     */
    private var needDefaultSubtitle = false

    /**
     * 是否需要「自动给次字幕挑一条」。
     *
     * **为什么要有它**：次字幕默认永远是「关闭」——`trySelectDefaultSubtitle()` 只管主字幕，
     * 于是双字幕开箱即用的场景（主=母语、次=目标语言）根本不会发生，用户得手动进
     * 「切换到次字幕设置…」选一次。真机实测反馈就是「次字幕不显示」，实际是没选。
     *
     * 置位条件与 [needDefaultSubtitle] 对称：本次没有恢复出已保存的次字幕来源。
     */
    private var needDefaultSecondary = false

    private var tickerJob: Job? = null
    private var errorCheckJob: Job? = null
    @Volatile private var released = false

    /** 是否曾经成功进入播放状态，用于区分「刚打开还没起来」与「真的失败了」。 */
    @Volatile private var everPlayed = false

    /** 自动选轨只做一次；之后尊重用户的手动选择。 */
    @Volatile private var autoAudioPicked = false

    /** 音量兜底只做一次，避免覆盖用户后来手动调的值。 */
    @Volatile private var volumeFixedOnce = false

    init {
        subtitleScope.launch {
            embeddedCues.state.collect { publishEmbeddedState(it) }
        }
        observeController()
        startPlayback()
        startTicker()
    }

    // ---------------------------------------------------------------- 播放控制

    fun togglePlayPause() {
        if (released) return
        controller.togglePlayPause()
    }

    fun seekBy(deltaMs: Long) {
        if (released) return
        seekTo((pendingSeek ?: controller.positionMs()) + deltaMs)
    }

    /**
     * 长按快进/退时的逐帧预览 seek。
     *
     * 与 [seekTo] 的区别：
     * - 无防抖延迟，直接送给解码器，让画面跟着手指实时走。
     * - 首次调用时自动暂停播放（记录在 [_wasPlayingBeforePreview]），松键后调
     *   [resumeFromPreview] 恢复。
     * - 暂停状态下 libVLC 每次 setTime 都会解码并渲染目标位置的关键帧，这就是"看到画面"。
     */
    fun seekPreview(deltaMs: Long) {
        if (released) return
        if (!_wasPlayingBeforePreview && controller.isPlaying()) {
            _wasPlayingBeforePreview = true
            controller.pause()
        }
        val duration = controller.durationMs()
        val target = ((pendingSeek ?: controller.positionMs()) + deltaMs)
            .let { if (duration > 0) it.coerceIn(0, duration) else it.coerceAtLeast(0) }
        pendingSeek = target
        // UI 上的时间条**每次按键都更新**，所以手感不受下面的节流影响。
        _positionMs.value = target

        // **节流（真机实测必需）**：遥控器长按按每秒钟十几次连发，而每一次 `setTime`
        // 都会让解码器重新定位、并从网络读回关键帧之前的一整段数据。本地 1080p 看不出
        // 问题，但 **4K + SMB** 片源上这个读取量会直接把 IO 压死 —— 表现就是「快进卡住」。
        //
        // 改成「固定间隔执行一次 + 永远只跳最新目标」：中间那些位置本来也不需要真的停，
        // 合并掉不减观感，却能把解码器 seek 次数降到原来的三分之一左右。
        if (previewJob?.isActive == true) return
        previewJob = subtitleScope.launch {
            while (isActive) {
                val t = pendingSeek ?: break
                controller.seekTo(t)
                delay(PREVIEW_THROTTLE_MS)
                if (pendingSeek == t) break      // 目标不再变 → 预览稳定，收工
            }
        }
    }

    /** 长按结束，如果预览前在播放则恢复播放。 */
    fun resumeFromPreview() {
        if (released) return
        previewJob?.cancel()
        previewJob = null
        if (_wasPlayingBeforePreview) {
            _wasPlayingBeforePreview = false
            controller.togglePlayPause() // pause → play
            pendingSeek?.let { refreshEmbeddedWindow(it) }
        }
    }

    // 长按预览期间是否暂停了播放（供 resumeFromPreview 判断）
    private var _wasPlayingBeforePreview = false

    /** 长按预览的节流任务：见 [seekPreview]。 */
    private var previewJob: Job? = null

    fun seekTo(positionMs: Long) {
        if (released) return
        val duration = controller.durationMs()
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        pendingSeek = target
        pendingSeekDeadline = android.os.SystemClock.elapsedRealtime() + 5000
        _positionMs.value = target
        seekJob?.cancel()
        // **这里刻意不清空字幕缓存**。原先那行 `embeddedCues.request(emptySet(), target)` 会让
        // 次字幕在「seek 完成 → 新窗口读回来」之间整段消失（真机上表现为「字幕闪一下没了」）。
        // 保留旧 cue 是安全的：ticker 是按**新位置**查 `cueAt()` 的，对不上自然不显示，
        // 对得上正好无缝续上。
        seekJob = subtitleScope.launch {
            delay(200) // Coalesce repeated remote-key presses into one decoder seek.
            controller.seekTo(target)
            refreshEmbeddedWindow(target)
        }
    }

    /** 把 libVLC 的视频画布挂到 Compose 里。 */
    fun attachVideoLayout(layout: VLCVideoLayout) {
        if (released) return
        controller.attachViews(layout)
        if (!viewAttached) {
            viewAttached = true
            // SurfaceView 就绪后再触发之前挂起的 open()
            pendingOpenArgs?.let { (uri, startMs, options) ->
                pendingOpenArgs = null
                controller.open(uri, startMs, options)
            }
        }
    }

    /**
     * 界面离开播放页（或切换到另一个视频）时调用：停掉刷新循环并释放播放器。
     *
     * 必须显式调用 —— [com.dualsub.tv.ui.AppRoot] 不再把本 VM 放进 Activity 级的
     * ViewModelStore，`onCleared()` 不保证及时触发，播放器会继续在后台读网络、继续出声。
     */
    fun startLiveAiSubtitle() {
        if (released) return
        if (_aiSubtitleState.value is AiSubtitleState.Running) {
            _aiSubtitleState.value = AiSubtitleState.LiveFailed("批处理进行中，请等待完成", canFallback = false)
            return
        }
        stopLiveAiSubtitle()
        viewModelScope.launch {
            val config = settings.aiConfig.first()
            if (config.apiKey.isBlank()) {
                _aiSubtitleState.value = AiSubtitleState.LiveFailed("未配置 API Key，请先在「网络 → AI 字幕设置」填写", canFallback = false)
                return@launch
            }
            updateTrack(forPrimary = false) {
                it.copy(
                    cues = emptyList(),
                    isLoading = true,
                    source = com.dualsub.tv.player.SubtitleSource.ExternalFile("ai://live", "AI 实时字幕")
                )
            }
            liveSession = AiRealtimeSubtitleSession(
                context = appContext,
                generator = services.aiSubtitleGenerator,
                mediaSources = services.mediaSources,
                videoUri = video.uri,
                config = config,
                durationMs = _durationMs.value,
                onCues = { cues -> pushLiveAiCues(cues) },
                onClear = { clearLiveCues() },
                onState = { state -> _aiSubtitleState.value = state }
            ).also { it.start(_positionMs.value) }
        }
    }

    fun stopLiveAiSubtitle() {
        liveSession?.stop()
        liveSession = null
        if (_aiSubtitleState.value is AiSubtitleState.Live) {
            _aiSubtitleState.value = AiSubtitleState.Idle
        }
    }

    private fun pushLiveAiCues(newCues: List<SubtitleCue>) {
        if (newCues.isEmpty()) return
        updateTrack(forPrimary = false) { track ->
            val merged = (track.cues + newCues).sortedBy { it.startMs }
            track.copy(cues = merged, isLoading = false)
        }
    }

    private fun clearLiveCues() {
        updateTrack(forPrimary = false) { it.copy(cues = emptyList()) }
    }

    fun generateAiSubtitle() {
        if (released) return
        if (_aiSubtitleState.value is AiSubtitleState.Running) return
        aiSubtitleJob?.cancel()
        aiSubtitleJob = viewModelScope.launch(Dispatchers.IO) {
            val config = settings.aiConfig.first()
            if (config.apiKey.isBlank()) {
                _aiSubtitleState.value = AiSubtitleState.Failed("未配置 API Key，请先在「网络 → AI 字幕设置」填写")
                return@launch
            }
            val result = services.aiSubtitleGenerator.generate(video.uri, config) { cur, total, phase ->
                _aiSubtitleState.value = AiSubtitleState.Running(cur, total, phase)
            }
            result.onSuccess { uri ->
                _aiSubtitleState.value = AiSubtitleState.Done(uri.path ?: "")
                selectExternalFile(uri, "AI 生成字幕", forPrimary = false)
            }.onFailure { e ->
                _aiSubtitleState.value = AiSubtitleState.Failed(e.message ?: "未知错误")
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        abandonAudioFocus()
        stopLiveAiSubtitle()
        tickerJob?.cancel()
        tickerJob = null
        errorCheckJob?.cancel()
        errorCheckJob = null
        subtitleScope.cancel()
        controller.release()
    }

    /** Activity/Fragment onStop 时调用：切到后台暂停播放。 */
    fun onAppBackground() {
        if (released) return
        if (controller.isPlaying()) {
            pausedForAudioFocus = true
            controller.pause()
        }
    }

    /** Activity/Fragment onStart 时调用：回到前台恢复播放。 */
    fun onAppForeground() {
        if (released) return
        if (pausedForAudioFocus) {
            pausedForAudioFocus = false
            if (!controller.isPlaying()) controller.togglePlayPause()
        }
    }

    // ---------------------------------------------------------------- 菜单操作

    /** 设置播放速率。 */
    fun setRate(value: Float) {
        if (released) return
        controller.setRate(value)
        refreshStats()
    }

    fun setVolume(value: Int) {
        if (released) return
        val clamped = value.coerceIn(0, 100)
        controller.setVolume(clamped)
        _stats.value = _stats.value.copy(volume = clamped)
    }

    /**
     * 手动切换音轨。
     *
     * 一旦用户手动选过，就不再让自动选轨干预 —— 用户的意图优先。
     */
    fun selectAudioTrack(id: Int) {
        if (released) return
        autoAudioPicked = true
        controller.selectAudioTrack(id)
        refreshStats()
        _audioTracks.value = controller.describeAudioTracks()
    }

    /** 设置显示比例；null 表示跟随视频原始比例。 */
    fun setAspectRatio(ratio: String?) {
        if (released) return
        controller.setAspectRatio(ratio)
    }

    // ---------------------------------------------------------------- 打开流程

    /**
     * 嗅探容器（**只用于诊断显示，不做拦截**），然后开始播放。
     *
     * 这里原先对 AVI / RMVB 做了「不支持就直接拦下」的处理，那是错的：实地反编译
     * media3-extractor 1.4.1 后确认它**有** `avi/AviExtractor`，而换成 libVLC 之后
     * 连 RMVB 都能播。所以现在只把「这是什么容器」显示出来，播不播交给播放器自己决定。
     */
    private fun startPlayback() {
        requestAudioFocus()
        restoreAndOpen()
        loadEmbeddedTrackList()
        viewModelScope.launch {
            val container = withContext(Dispatchers.IO) { sniffContainer() }
            if (released) return@launch
            _container.value = container
            Log.i(TAG, "容器嗅探结果：${container ?: "未识别"}")

        }
    }

    private fun restoreAndOpen() {
        viewModelScope.launch {
            val primaryStyle = settings.primaryStyle.first()
            val secondaryStyle = settings.secondaryStyle.first()
            val primarySource = settings.primarySource(video.storageKey).first()
            val hasPrimarySource = settings.hasPrimarySource(video.storageKey).first()
            val secondarySource = settings.secondarySource(video.storageKey).first()

            _primary.value = _primary.value.copy(style = primaryStyle)
            _secondary.value = _secondary.value.copy(style = secondaryStyle)

            if (released) return@launch

            // SurfaceView 可能还没 attach（AndroidView 要等第一次布局才回调），
            // 所以先检查；若未就绪则存入 pending，等 attachVideoLayout() 时触发。
            val openArgs = Triple(video.uri, 0L, emptyList<String>())
            if (viewAttached) {
                controller.open(openArgs.first, openArgs.second, openArgs.third)
            } else {
                pendingOpenArgs = openArgs
            }

            if (!primarySelectionMade) applySource(primarySource, forPrimary = true)
            if (!secondarySelectionMade) applySource(secondarySource, forPrimary = false)
            needDefaultSubtitle = !hasPrimarySource && !primarySelectionMade
            // 次字幕同理：没恢复出已保存的来源时，稍后自动挑一条「与主字幕语言不同」的。
            // 注意次字幕没有 hasXxxSource 之类的查询，直接看恢复出来的来源是不是 None 即可。
            needDefaultSecondary = !secondarySelectionMade && secondarySource is SubtitleSource.None
            sourcesRestored = true
            trySelectDefaultSubtitle()
        }
    }

    /** 刷新 libVLC 只读快照。走 JNI，所以只在状态变化时调用，绝不在组合期调用。 */
    private fun refreshStats() {
        if (released) return
        val size = controller.videoSize()
        _stats.value = PlaybackStats(
            audioOptions = controller.audioTrackOptions(),
            currentAudioId = controller.currentAudioTrackId(),
            videoSize = if (size != null) "${size.first}x${size.second}" else "尚未取得",
            volume = controller.volume(),
            rate = controller.rate(),
            streamSummary = controller.streamStatsText(),
            inputBytesPerSec = controller.inputBytesPerSec()
        )
    }

    /** 供界面在打开信息层时主动刷新一次快照（码率这类值只在播放中才准）。 */
    fun refreshStatsNow() = refreshStats()

    /**
     * 只刷新「实时网速」这一个字段。
     *
     * 与 [refreshStats] 分开是为了省开销：后者要枚举全部音轨、查分辨率，一次好几个 JNI；
     * 而网速每秒都要更新，只该付出取一个 `input_bitrate` 的代价。
     */
    private fun refreshNetworkSpeed() {
        if (released) return
        val speed = controller.inputBytesPerSec()
        val current = _stats.value
        if (current.inputBytesPerSec != speed) {
            _stats.value = current.copy(inputBytesPerSec = speed)
        }
    }

    // ---------------------------------------------------------------- 容器嗅探

    /**
     * 读文件头判断封装格式，只读前 16 字节，本地与 SMB 片源都够快。
     * 识别不出返回 null，不影响任何播放流程。
     */
    private fun sniffContainer(): String? = runCatching {
        val source = openVideoDataSource()
        try {
            val head = ByteArray(16)
            val count = source.readAt(0, head, 0, head.size)
            if (count < 12) return@runCatching null

            fun at(i: Int) = head[i].toInt() and 0xFF

            when {
                // "RIFF" .... "AVI " / "AVIX"
                at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                    at(8) == 0x41 && at(9) == 0x56 && at(10) == 0x49 -> "AVI"

                // 1A 45 DF A3 —— Matroska / WebM
                at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> "MKV"

                // .... "ftyp" —— MP4 / MOV / M4V
                at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70 -> "MP4"

                // 30 26 B2 75 —— ASF（WMV / WMA）
                at(0) == 0x30 && at(1) == 0x26 && at(2) == 0xB2 && at(3) == 0x75 -> "ASF"

                // ".RMF" —— RealMedia
                at(0) == 0x2E && at(1) == 0x52 && at(2) == 0x4D && at(3) == 0x46 -> "RMVB"

                // "FLV"
                at(0) == 0x46 && at(1) == 0x4C && at(2) == 0x56 -> "FLV"

                // "OggS"
                at(0) == 0x4F && at(1) == 0x67 && at(2) == 0x67 && at(3) == 0x53 -> "OGG"

                else -> null
            }
        } finally {
            runCatching { source.close() }
        }
    }.getOrElse { error ->
        Log.w(TAG, "容器嗅探失败（不影响播放）：${error.message}")
        null
    }

    // ---------------------------------------------------------------- 字幕来源

    fun selectNone(forPrimary: Boolean) = setSource(SubtitleSource.None, forPrimary)

    fun selectEmbedded(track: EmbeddedSubtitleReader.TrackInfo, forPrimary: Boolean) {
        setSource(
            SubtitleSource.EmbeddedTrack(
                // Only bitmap primary subtitles use VLC ids; text tracks use Media3 indices.
                trackIndex = if (forPrimary && track.isBitmap) mapToVlcSubtitleTrack(track) ?: track.index else track.index,
                mimeType = track.mimeType,
                language = track.language,
                label = track.label
            ),
            forPrimary
        )
    }

    /**
     * 把自写解析器列出的内嵌轨对齐到 libVLC 的字幕轨 id。
     *
     * **为什么不能只看语言码**：真机踩过 —— 某片源有 3 条 `zh` 轨（简体 / 繁体 / 繁体），
     * 语言码不唯一时若直接退回「按全局序号对齐」，两套列表序号一旦错位，就会把一个
     * **不存在**的 id 交给 `setSpuTrack()`，界面直接报「libVLC 选不中该字幕轨」。
     *
     * 对齐优先级：
     * 1. **轨名精确匹配** —— 两边的名字都源自 MKV 的 `Name` 元素（Media3 放进 `label`、
     *    libVLC 放进 `spuTracks[].name`），这是最可靠的锚点，且要求命中唯一；
     * 2. 语言码 + **同语言内的相对序号**（比全局序号稳得多）；
     * 3. 全局序号（最后兜底）。
     */
    private fun mapToVlcSubtitleTrack(track: EmbeddedSubtitleReader.TrackInfo): Int? {
        val candidates = controller.subtitleTrackDetails()
        if (candidates.isEmpty()) return null

        // ① 轨名精确匹配（唯一命中才采用，否则说明重名，改用下面的序号法）
        val label = track.label?.takeIf { it.isNotBlank() }
        if (label != null) {
            val byName = candidates.filter { it.description == label }
            if (byName.size == 1) {
                Log.i(TAG, "主字幕轨对齐：轨名「$label」→ libVLC spu=${byName.first().id}")
                return byName.first().id
            }
        }

        // ② 同语言内的相对序号
        val language = track.language?.takeIf { it.isNotBlank() && it != "und" }
        if (language != null) {
            val sameLangLocal = _embeddedTracks.value.filter { it.language == language }
            val sameLangRemote = candidates.filter { it.language == language }
            val localOrder = sameLangLocal.indexOfFirst { it.index == track.index }
            if (localOrder >= 0 && localOrder < sameLangRemote.size) {
                val mapped = sameLangRemote[localOrder].id
                Log.i(TAG, "主字幕轨对齐：语言 $language 内第 ${localOrder + 1} 条 → libVLC spu=$mapped")
                return mapped
            }
        }

        // ③ 全局序号兜底
        val order = _embeddedTracks.value.indexOfFirst { it.index == track.index }
        val mapped = candidates.getOrNull(order)?.id
        Log.i(TAG, "主字幕轨对齐：全局序号 $order → libVLC spu=$mapped（语言 ${track.language}）")
        return mapped
    }

    fun selectExternalFile(uri: Uri, displayName: String, forPrimary: Boolean) {
        setSource(SubtitleSource.ExternalFile(uri.toString(), displayName), forPrimary)
    }

    private fun setSource(source: SubtitleSource, forPrimary: Boolean) {
        if (released) return
        if (forPrimary) {
            primarySelectionMade = true
            needDefaultSubtitle = false
        } else {
            secondarySelectionMade = true
            needDefaultSecondary = false
        }
        viewModelScope.launch {
            if (forPrimary) {
                settings.setPrimarySource(video.storageKey, source)
            } else {
                settings.setSecondarySource(video.storageKey, source)
            }
        }
        applySource(source, forPrimary)
    }

    private fun applySource(savedSource: SubtitleSource, forPrimary: Boolean) {
        val source = normalizeTextSource(savedSource)
        lastPublishedEmbeddedState = null
        if (forPrimary) primaryLoadJob?.cancel() else secondaryLoadJob?.cancel()
        updateTrack(forPrimary) {
            it.copy(source = source, cues = emptyList(), error = null, isLoading = source !is SubtitleSource.None)
        }

        // **两路字幕现在共用同一条链路**：内嵌 / 外挂字幕都由自写解析器读成 cues，
        // 再交给 Compose 叠加层自绘 —— 字体走**系统字体栈**，任何能正常显示中文界面的
        // 电视都能正确渲染，不会再出现方框；两路的样式与位置也能统一排版、避免上下打架。
        //
        // 唯一的例外是**图片字幕**（PGS / VOBSUB / DVBSUB）：位图 Compose 画不了，
        // 仍由 libVLC 选轨渲染（见 applyPrimaryToPlayer）。
        if (forPrimary) {
            primaryLoadJob = applyPrimaryToPlayer(source)
        } else {
            secondaryLoadJob = when (source) {
                SubtitleSource.None -> null
                is SubtitleSource.ExternalFile -> loadExternal(source, forPrimary = false)
                is SubtitleSource.EmbeddedTrack -> null
            }
            refreshEmbeddedWindow(pendingSeek ?: controller.positionMs())
        }
    }

    /**
     * 应用主字幕的来源。
     *
     * **现在主字幕默认走自绘**（与次字幕同一套 Compose 渲染），所以这里的职责变成
     * 「决定这一路是自绘还是交给 libVLC」：
     *
     * - **文本内嵌轨**（SRT / ASS / SSA）→ 自绘。先把 libVLC 的字幕渲染关掉、再拉字幕窗口；
     * - **图片内嵌轨**（PGS / VOBSUB / DVBSUB）→ 交给 libVLC 的 `setSpuTrack()`，自绘画不了位图；
     * - **外挂文件** → 自绘（不再落盘挂 slave）；
     * - **关闭** → `setSpuTrack(-1)`。
     *
     * **关闭也必须显式处理**：去掉 `--no-spu` 之后 libVLC 会按自己的语言偏好自动挑一条
     * 内嵌字幕轨，用户没选主字幕时那条会凭空冒出来。
     *
     * 返回值是「加载任务」，仅外挂文件那条路会真正起协程。
     */
    private fun applyPrimaryToPlayer(source: SubtitleSource): Job? = when (source) {
        SubtitleSource.None -> {
            controller.selectSubtitleTrack(-1)
            updateTrack(forPrimary = true) {
                it.copy(cues = emptyList(), isLoading = false, error = null)
            }
            null
        }

        is SubtitleSource.EmbeddedTrack -> {
            // **按字幕类型分两条路**：
            //
            // - **文本字幕**（SRT / ASS / SSA）→ **走自绘**。原先交给 libVLC 的 libass，
            //   但在没有中文字体的电视上会整片渲染成方框，而 VLC 在 Android 上的字体链路
            //   我们够不到。改自绘后字体走系统字体栈，方框消失，且样式/位置与次字幕统一。
            // - **图片字幕**（PGS / VOBSUB / DVBSUB）→ 位图 Compose 画不了，**仍交 libVLC**。
            if (SubtitleFormat.isBitmapMime(source.mimeType)) {
                val ok = controller.selectSubtitleTrack(source.trackIndex)
                updateTrack(forPrimary = true) {
                    it.copy(
                        cues = emptyList(), isLoading = false,
                        error = if (ok) null else "libVLC 选不中该字幕轨"
                    )
                }
                null
            } else {
                // 先关掉 libVLC 自己的字幕渲染，否则它会和自绘的那层叠在一起显示两份。
                controller.selectSubtitleTrack(-1)
                updateTrack(forPrimary = true) {
                    it.copy(cues = emptyList(), isLoading = true, error = null)
                }
                // 立刻拉一次窗口；之后 ticker 会持续补读。
                refreshEmbeddedWindow(pendingSeek ?: controller.positionMs())
                null
            }
        }

        is SubtitleSource.ExternalFile -> {
            // 外挂字幕也是文本，一并走自绘（不再挂给 libVLC 作 slave）。
            controller.selectSubtitleTrack(-1)
            loadExternal(source, forPrimary = true)
        }
    }

    /**
     * 把外挂字幕文件复制到应用缓存目录，返回本地路径。
     *
     * libVLC 的 subtitle slave 只认它自己能打开的路径 / MRL，**不认 `content://`**
     * （SAF 返回的正是这个），直接传会静默失败。字幕文件很小，放 cacheDir 由系统回收。
     */
    @Suppress("unused")
    private fun stageSubtitleFile(source: SubtitleSource.ExternalFile): String? = runCatching {
        val extension = source.displayName.substringAfterLast('.', "srt")
        val target = File(appContext.cacheDir, "primary-subtitle-${source.uri.hashCode()}.$extension")
        val input = appContext.contentResolver.openInputStream(Uri.parse(source.uri))
            ?: return@runCatching null
        input.use { from -> target.outputStream().use { to -> from.copyTo(to) } }
        Log.i(TAG, "主字幕已落盘：${target.name}（${target.length()} 字节）")
        target.absolutePath
    }.getOrElse { error ->
        Log.w(TAG, "主字幕落盘失败：${error.message}")
        null
    }

    private fun loadExternal(source: SubtitleSource.ExternalFile, forPrimary: Boolean): Job =
        subtitleScope.launch {
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
                    if (error is CancellationException) throw error
                    updateTrack(forPrimary) {
                        it.copy(cues = emptyList(), isLoading = false, error = "读取字幕失败：${error.message}")
                    }
                }
        }

    /** Repair selections saved when text-primary sources accidentally stored VLC ids. */
    private fun normalizeTextSource(source: SubtitleSource): SubtitleSource {
        if (source !is SubtitleSource.EmbeddedTrack || SubtitleFormat.isBitmapMime(source.mimeType)) return source
        val match = _embeddedTracks.value.singleOrNull { !it.isBitmap && it.label == source.label } ?: return source
        return if (source.trackIndex == match.index) source else source.copy(trackIndex = match.index)
    }

    private var lastPublishedEmbeddedState: EmbeddedCueState? = null
    private var lastPublishedPrimarySource: SubtitleSource? = null
    private var lastPublishedSecondarySource: SubtitleSource? = null

    private fun refreshEmbeddedWindow(position: Long) {
        if (!tracksReady || released) return
        val selected = listOf(_primary.value.source, _secondary.value.source)
            .filterIsInstance<SubtitleSource.EmbeddedTrack>()
            .filterNot { SubtitleFormat.isBitmapMime(it.mimeType) }
            .map { it.trackIndex }.toSet()
        embeddedCues.request(selected, position)
        publishEmbeddedState(embeddedCues.state.value)
    }

    /**
     * 把窗口读取结果分发到两路。
     *
     * 两路现在共用同一条自写解析链路（主字幕只在「图片轨」时才仍归 libVLC），
     * 所以更新逻辑对二者完全对称 —— 谁在 [EmbeddedCueState.tracks] 里就更新谁。
     */
    private fun publishEmbeddedState(state: EmbeddedCueState) {
        val primarySource = _primary.value.source
        val secondarySource = _secondary.value.source
        if (state === lastPublishedEmbeddedState && primarySource == lastPublishedPrimarySource &&
            secondarySource == lastPublishedSecondarySource) return
        lastPublishedEmbeddedState = state
        lastPublishedPrimarySource = primarySource
        lastPublishedSecondarySource = secondarySource
        for (forPrimary in listOf(true, false)) {
            updateTrack(forPrimary) { track ->
                val source = track.source as? SubtitleSource.EmbeddedTrack
                if (source == null || source.trackIndex !in state.tracks) track else track.copy(
                    cues = state.cues[source.trackIndex].orEmpty(),
                    isLoading = state.isLoading, error = state.error
                )
            }
        }
    }
    private fun deliver(cues: List<SubtitleCue>, forPrimary: Boolean) {
        Log.i(TAG, "字幕交付：${if (forPrimary) "主" else "次"}字幕 ${cues.size} 条")
        updateTrack(forPrimary) {
            it.copy(
                cues = cues,
                isLoading = false,
                error = if (cues.isEmpty()) "该来源没有解析出字幕条目" else null
            )
        }
    }

    private fun loadEmbeddedTrackList() {
        trackListJob?.cancel()
        tracksReady = false
        embeddedCues.request(emptySet(), 0)
        _embeddedTrackStatus.value = "正在读取轨道…"
        trackListJob = subtitleScope.launch {
            try {
                val tracks = withTimeout(20_000) {
                    withContext(Dispatchers.IO) {
                        subtitleIo.withLock { EmbeddedSubtitleReader.listTracks(appContext, ::openVideoDataSource) }
                    }
                }
                _embeddedTracks.value = tracks
                tracksReady = true
                for (primary in listOf(true, false)) {
                    val old = if (primary) _primary.value.source else _secondary.value.source
                    val corrected = normalizeTextSource(old)
                    if (corrected != old) {
                        applySource(corrected, primary)
                        if (primary) settings.setPrimarySource(video.storageKey, corrected)
                        else settings.setSecondarySource(video.storageKey, corrected)
                    }
                }
                _embeddedTrackStatus.value = if (tracks.isEmpty()) "未发现支持的字幕轨" else "${tracks.size} 条"
                Log.i(TAG, "内嵌字幕轨 ${tracks.size} 条：" +
                    tracks.joinToString { "#${it.index} ${it.format}/${it.language}" })
                trySelectDefaultSubtitle()
                refreshEmbeddedWindow(pendingSeek ?: controller.positionMs())
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                _embeddedTrackStatus.value = "读取超时，请重试"
                failEmbeddedTracks("读取字幕轨超时，请重试")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 具体异常只进日志：SMB 抛出的 message 里会夹着
                // `com.hieronymus.protocol.transport.TransportException: Cannot write Signed(…)`
                // 这种用户看不懂、也照做不了的东西。屏上只给一句能照着做的提示。
                Log.w(TAG, "读取内嵌字幕轨失败", error)
                _embeddedTrackStatus.value = "读取失败，可点下面「重新读取字幕轨」重试"
                _notice.value = "读取内嵌字幕轨失败 —— 可在「字幕设置」里点「重新读取字幕轨」"
                failEmbeddedTracks("读取字幕轨失败，请重试")
            }
        }
    }

    fun retryEmbeddedTracks() { if (!released) loadEmbeddedTrackList() }

    /**
     * 立刻收掉当前提示条。
     *
     * 提示条是**自动出现**的（自动选轨、字幕轨读取失败之类），没有"确定"按钮 ——
     * 用户不想等它就按返回。返回键的层级会先落到这里（见 `PlayerScreen` 的 `BackHandler`）。
     */
    fun dismissNotice() { _notice.value = null }

    private fun failEmbeddedTracks(message: String) {
        for (primary in listOf(true, false)) updateTrack(primary) { track ->
            if (track.source is SubtitleSource.EmbeddedTrack) track.copy(isLoading = false, error = message)
            else track
        }
    }

    /**
     * 打开媒体后给两路字幕各挑一条默认轨道。
     *
     * - **主字幕**：优先中文，没有就退第一条（原有行为）。
     * - **次字幕**：挑一条**语言与主字幕不同**的，优先英文 —— 这是双字幕最常见的用法
     *   （主=母语、次=目标语言）。此前次字幕默认恒为「关闭」，用户会以为功能坏了。
     *
     * 两路各自有标志位，**用户保存过的来源不会被覆盖**。
     */
    private fun trySelectDefaultSubtitle() {
        if (released || !sourcesRestored) return
        val tracks = _embeddedTracks.value.filterNot { it.isBitmap }
        if (tracks.isEmpty()) return

        if (needDefaultSubtitle) {
            val preferred = tracks.firstOrNull { isChinese(it.language) } ?: tracks.first()
            needDefaultSubtitle = false
            selectEmbedded(preferred, forPrimary = true)
        }

        if (needDefaultSecondary) {
            // **只挑英文**：双字幕最常用的组合是「主=母语、次=英文」。
            // 找不到就**保持关闭**，交给用户手动选 —— 不做「退而求其次挑第一条非中文语言」，
            // 那会在没有英文轨的片源上挑出丹麦语 / 阿拉伯语这种莫名其妙的次字幕
            //（真机上就出现过次字幕变成 `da` / `ar`）。
            // 另外要求语言**不同于主字幕**，避免主次两路撞成同一条。
            val primaryLang = (_primary.value.source as? SubtitleSource.EmbeddedTrack)?.language?.lowercase()
            val english = tracks.firstOrNull {
                isEnglish(it.language) && it.language?.lowercase() != primaryLang
            }
            if (english != null) {
                needDefaultSecondary = false
                selectEmbedded(english, forPrimary = false)
            }
        }
    }

    /** 语言码是否中文（含 ISO 639-1/2 与 BCP-47 写法）。 */
    private fun isChinese(language: String?): Boolean {
        val l = language?.lowercase().orEmpty()
        return l.startsWith("zh") || l == "chi" || l == "zho" || l == "cmn"
    }

    /** 语言码是否英文。 */
    private fun isEnglish(language: String?): Boolean {
        val l = language?.lowercase().orEmpty()
        return l.startsWith("en") || l == "eng"
    }

    /** 按当前片源的 scheme 造一个随机读数据源（用于读内嵌字幕）。 */
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
        // 主字幕由 libVLC 渲染，偏移必须同步给它 —— 否则只有界面上的数字在变，字幕不动。
        if (forPrimary) controller.setSubtitleDelayMs(_primary.value.offsetMs)
    }

    // ---------------------------------------------------------------- 内部

    private fun updateTrack(forPrimary: Boolean, transform: (SubtitleTrack) -> SubtitleTrack) {
        if (released) return
        if (forPrimary) {
            _primary.value = transform(_primary.value)
        } else {
            _secondary.value = transform(_secondary.value)
        }
    }

    /**
     * 把 libVLC 当前选中的字幕轨纠正回「我们期望它渲染的那一路」。
     *
     * **改自绘之后，这个函数的职责只剩下图片字幕**：
     * - **文本字幕**（SRT / ASS / SSA）：已由 Compose 自绘，libVLC 那边必须保持 `-1`（关掉），
     *   否则它会和自绘的那层叠着显示两份；
     * - **图片字幕**（PGS / VOBSUB / DVBSUB）：位图我们画不了，仍归 libVLC，这时才需要
     *   把它自动挑的轨纠正成用户选的那条；
     * - **外挂字幕**：同样走自绘，libVLC 保持关闭。
     *
     * 另外，去掉 `--no-spu` 之后 VLC 会按语言偏好自己挑一条内嵌轨，所以「关闭」也要显式纠正。
     */
    private fun enforcePrimarySubtitleSelection() {
        val want = when (val source = _primary.value.source) {
            SubtitleSource.None -> -1
            is SubtitleSource.EmbeddedTrack ->
                if (SubtitleFormat.isBitmapMime(source.mimeType)) source.trackIndex else -1
            is SubtitleSource.ExternalFile -> -1
        }
        if (controller.currentSubtitleTrackId() != want) {
            Log.i(TAG, "纠正主字幕轨：${controller.currentSubtitleTrackId()} → $want")
            controller.selectSubtitleTrack(want)
        }
    }

    private fun observeController() {
        controller.onState = { playing, buffering ->
            _isPlaying.value = playing
            _isBuffering.value = buffering
            if (playing) {
                everPlayed = true
                _playerError.value = null
                ensureAudibleVolume()
            }
        }

        controller.onLength = { length ->
            if (length > 0L) _durationMs.value = length
        }

        controller.onTracks = { description ->
            _mediaInfo.value = description
            _audioTracks.value = controller.describeAudioTracks()

            // 首次拿到音轨信息时，自动挑一条最可能出声的。
            //
            // 背景：MKV 常把 DTS-HD / TrueHD 这类只能软解的音轨放在第一位，而 VLC 默认
            // 就选第一条；一旦软解链路有问题，表现就是「有画面没声音」。而文件里往往还
            // 带了一条 AC3 / AAC —— 那条电视能直接出声。判断依据是 IMedia.tracks 里的
            // 编码名（见 VlcPlayerController.autoSelectBestAudioTrack）。
            if (!autoAudioPicked) {
                autoAudioPicked = true
                controller.autoSelectBestAudioTrack()?.let { reason ->
                    Log.i(TAG, reason)
                    _notice.value = reason
                    _audioTracks.value = controller.describeAudioTracks()
                }
            }

            // 字幕轨此时也已解出：把 libVLC 的当前选择纠正回我们记录的主字幕源
            // （去掉 `no-spu` 后 VLC 会自己挑一条内嵌字幕轨，不纠正就会多出一条字幕）。
            enforcePrimarySubtitleSelection()

            // 音轨/尺寸信息到这里才算稳定，统一刷新一次快照给界面用
            refreshStats()
        }

        controller.onEndReached = {
            _isPlaying.value = false
        }

        controller.onError = { message ->
            _isBuffering.value = false
            // libVLC 的报错很笼统，而且有时早于轨道信息到达；稍等一下再判定，
            // 这样能拿到「已经解到哪一步」写进详情，也避免误报。
            errorCheckJob?.cancel()
            errorCheckJob = viewModelScope.launch {
                delay(ERROR_SETTLE_DELAY_MS)
                if (released || everPlayed) return@launch
                _playerError.value = describeFailure(message)
                Log.w(TAG, "播放失败：${_playerError.value?.detail}")
            }
        }
    }

    /**
     * 确保 libVLC 这一路不是静音。
     *
     * **这是「有画面没声音」最常见的真因**：libVLC 的媒体音量初始可能就是 0，而我们从未
     * 设置过它。而遥控器上的音量键调的是**电视系统音量**，与 VLC 内部音量是两个独立通道 ——
     * 所以电视音量条在动，VLC 那一路依然是 0，结果就是完全没声音。
     *
     * 只在「原本就是 0」时补一次，避免覆盖用户手动调过的值。
     */
    private fun ensureAudibleVolume() {
        if (released || volumeFixedOnce) return
        volumeFixedOnce = true
        val current = controller.volume()
        if (current <= 0) {
            Log.i(TAG, "libVLC 音量为 $current，主动置为 100")
            controller.setVolume(100)
            // 这里故意**不弹提示**（原先会弹「已把播放器音量从 0 调到 100%…」）：音量是自动
            // 修好的状态，说出来只会让人以为出了问题；左上角也要留给文件名。排查时看同一句 Log.i。
        }
        refreshStats()
    }

    /**
     * 把一次播放失败翻译成「结论 + 原因 + 建议」。
     *
     * libVLC 不像 Media3 那样给细分错误码，所以判断主要靠三样东西：
     * 嗅探到的容器、已解出的编码、以及是否曾经成功播放过。
     */
    private fun describeFailure(rawMessage: String): PlaybackFailure {
        val container = _container.value
        val info = _mediaInfo.value?.takeIf { it != "尚未取得轨道信息" }

        val detail = buildString {
            append(rawMessage)
            container?.let { append(" ｜ 容器=").append(it) }
            info?.let { append(" ｜ 轨道=").append(it) }
        }

        // 已经解出轨道信息却仍然失败 → 容器读懂了，卡在解码
        if (info != null) {
            return PlaybackFailure(
                title = "解码失败",
                reason = "文件结构已经读懂了（$info），但其中的音视频编码没能解出来。",
                suggestion = "请对照「影片详情」里显示的编码：若是 AV1 等这代芯片不支持的编码，" +
                    "需要额外接入视频软解扩展；也可以先确认这个文件在电脑上的播放器里是否正常。",
                detail = detail,
                mediaInfo = info
            )
        }

        // 完全没拿到轨道信息 → 卡在读文件 / 解析容器阶段
        val hint = container
        return PlaybackFailure(
            title = "这个文件打不开",
            reason = if (hint != null) {
                "连文件结构都没能读出来（已识别为 $hint 容器）。可能文件损坏、下载不完整，或者片源读取中断。"
            } else {
                "连文件结构都没能读出来。可能文件损坏、下载不完整，或者片源读取中断。"
            },
            suggestion = "若是网络片源，先确认这个共享还能正常访问（可到「网络位置」里重新测试连接）；" +
                "也可以把文件拷到 U 盘上用本机播放，以排除网络因素。",
            detail = detail,
            mediaInfo = info
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var lastSpeedPollAt = 0L
            while (isActive) {
                if (released) break
                val actualPosition = controller.positionMs()
                pendingSeek?.let { target ->
                    if (seekJob?.isActive != true && (kotlin.math.abs(actualPosition - target) < 2000 ||
                            android.os.SystemClock.elapsedRealtime() >= pendingSeekDeadline)) pendingSeek = null
                }
                val position = pendingSeek ?: actualPosition
                _positionMs.value = position
                if (seekJob?.isActive != true) refreshEmbeddedWindow(position)
                // 两路字幕都由自绘渲染，所以两路都要按当前位置取 cue。
                _primaryCue.value = _primary.value.cueAt(position)
                _secondaryCue.value = _secondary.value.cueAt(position)
                liveSession?.updatePosition(position)
                // 网速节流到约 1 秒一次：ticker 是 50ms 一跳，而这个值要读 libVLC 的 stats（JNI）。
                // 每跳都取会把每秒几十次 JNI 的预算吃光，而网速本来也不需要 20Hz 的刷新率。
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastSpeedPollAt >= SPEED_POLL_INTERVAL_MS) {
                    lastSpeedPollAt = now
                    refreshNetworkSpeed()
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

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private companion object {
        /** 字幕定位刷新间隔：50ms 约等于 20 次/秒，肉眼足够顺滑且几乎不占 CPU。 */
        const val TICK_INTERVAL_MS = 50L

        /** 「实时网速」的取样间隔。ticker 是 50ms 一跳，网速没必要跟着那么快（理由见 [startTicker]）。 */
        const val SPEED_POLL_INTERVAL_MS = 1000L

        /** 收到 libVLC 报错后等这么久再判定，以便拿到轨道信息写进详情。 */
        const val ERROR_SETTLE_DELAY_MS = 1200L

        /**
         * 长按快进/退时的 seek 节流间隔。
         *
         * 遥控器长按连发约每秒十余次；若每次都真的让解码器跳一次，在 SMB + 4K 片源上
         * 会把 IO 压死（真机实测「快进卡住」就是这个）。150ms 约合 6~7 次/秒，
         * 观感仍连贯，但解码器负担降到三分之一左右。
         */
        const val PREVIEW_THROTTLE_MS = 150L

        const val TAG = "DualSubTV"
    }
}
