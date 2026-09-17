package com.dualsub.tv.ui.player

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.EmbeddedSubtitleReader
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.player.SubtitleSource
import com.dualsub.tv.player.EmbeddedCueCache
import com.dualsub.tv.player.EmbeddedCueState
import com.dualsub.tv.player.SubtitleStyle
import com.dualsub.tv.player.SubtitleTrack
import com.dualsub.tv.player.VlcPlayerController
import com.dualsub.tv.subtitle.SubtitleCue
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
    val streamSummary: String? = null
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

    private val settings = services.settings
    private val controller = VlcPlayerController(appContext)

    private val _primary = MutableStateFlow(SubtitleTrack(style = SubtitleStyle.PRIMARY))
    val primary: StateFlow<SubtitleTrack> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow(SubtitleTrack(style = SubtitleStyle.SECONDARY))
    val secondary: StateFlow<SubtitleTrack> = _secondary.asStateFlow()

    private val _primaryCue = MutableStateFlow<SubtitleCue?>(null)
    val primaryCue: StateFlow<SubtitleCue?> = _primaryCue.asStateFlow()

    private val _secondaryCue = MutableStateFlow<SubtitleCue?>(null)
    val secondaryCue: StateFlow<SubtitleCue?> = _secondaryCue.asStateFlow()

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

    private var tickerJob: Job? = null
    private var errorCheckJob: Job? = null
    private var released = false

    /** 是否曾经成功进入播放状态，用于区分「刚打开还没起来」与「真的失败了」。 */
    private var everPlayed = false

    /** 自动选轨只做一次；之后尊重用户的手动选择。 */
    private var autoAudioPicked = false

    /** 音量兜底只做一次，避免覆盖用户后来手动调的值。 */
    private var volumeFixedOnce = false

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

    fun seekTo(positionMs: Long) {
        if (released) return
        val duration = controller.durationMs()
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        pendingSeek = target
        pendingSeekDeadline = android.os.SystemClock.elapsedRealtime() + 5000
        _positionMs.value = target
        seekJob?.cancel()
        embeddedCues.request(emptySet(), target)
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
        errorCheckJob?.cancel()
        errorCheckJob = null
        subtitleScope.cancel()
        controller.release()
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
            settings.clearPlaybackPositions()
            val primaryStyle = settings.primaryStyle.first()
            val secondaryStyle = settings.secondaryStyle.first()
            val primarySource = settings.primarySource(video.storageKey).first()
            val hasPrimarySource = settings.hasPrimarySource(video.storageKey).first()
            val secondarySource = settings.secondarySource(video.storageKey).first()

            _primary.value = _primary.value.copy(style = primaryStyle)
            _secondary.value = _secondary.value.copy(style = secondaryStyle)

            if (released) return@launch

            // libVLC 自己认识 smb://（内置 SMB 模块），也认识 file:// / content:// / http://
            controller.open(
                uri = video.uri,
                startPositionMs = 0L,
                options = playbackOptions()
            )

            if (!primarySelectionMade) applySource(primarySource, forPrimary = true)
            if (!secondarySelectionMade) applySource(secondarySource, forPrimary = false)
            needDefaultSubtitle = !hasPrimarySource && !primarySelectionMade
            sourcesRestored = true
            trySelectDefaultSubtitle()
        }
    }

    /**
     * 传给 libVLC 的媒体级选项。
     *
     * 实测 SMB 片源能正常播放，说明 libVLC 从 URI 就能拿到所需信息，不必额外注入凭据。
     * 若将来遇到需要显式账号密码的共享，再在这里补 `:smb-user=` / `:smb-pwd=`。
     */
    private fun playbackOptions(): List<String> = emptyList()

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
            streamSummary = controller.streamStatsText()
        )
    }

    /** 供界面在打开信息层时主动刷新一次快照（码率这类值只在播放中才准）。 */
    fun refreshStatsNow() = refreshStats()

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
                // **主字幕必须换成 libVLC 的轨号**：主字幕由 libVLC 渲染，它用的是自己的
                // spu track id，而这里的 track.index 是 Media3 的全局轨道索引，两者不是一套。
                // 次字幕走自写解析器，仍用原索引。
                trackIndex = if (forPrimary) mapToVlcSubtitleTrack(track) ?: track.index else track.index,
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
     * 对齐策略：先按语言码（`chi` / `eng` …）匹配；语言码缺失或有歧义（多条同语言）时，
     * 退回按容器内顺序对齐 —— 两边都是按容器轨道顺序列出的，顺序一致。
     */
    private fun mapToVlcSubtitleTrack(track: EmbeddedSubtitleReader.TrackInfo): Int? {
        val candidates = controller.subtitleTrackDetails()
        if (candidates.isEmpty()) return null

        val language = track.language?.takeIf { it.isNotBlank() && it != "und" }
        if (language != null) {
            val sameLanguage = candidates.filter { it.language == language }
            if (sameLanguage.size == 1) return sameLanguage.first().id
        }

        val order = _embeddedTracks.value.indexOfFirst { it.index == track.index }
        val mapped = candidates.getOrNull(order)?.id
        Log.i(TAG, "主字幕轨对齐：Media3 index=${track.index} → libVLC spu=$mapped（语言 ${track.language}）")
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
        } else secondarySelectionMade = true
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
        if (forPrimary) primaryLoadJob?.cancel() else secondaryLoadJob?.cancel()
        updateTrack(forPrimary) {
            it.copy(source = source, cues = emptyList(), error = null, isLoading = source !is SubtitleSource.None)
        }

        // **主次字幕走两条不同的链路**：
        // - 主字幕交给 libVLC（内嵌轨选轨 / 外挂文件作 slave），由它的 libass 渲染 ——
        //   这样才拿得到完整 ASS 特效、容器内嵌字体，以及图片字幕（PGS / DVD SPU / 蓝光）。
        //   代价是主字幕**不再需要 cues**，自绘链路也不再碰它。
        // - 次字幕仍由自写解析器读成 cues、再由 Compose 叠加层自绘，样式与位置保持可独立调。
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
     * 把主字幕交给 libVLC。
     *
     * - 内嵌轨 → `setSpuTrack()` 选轨；
     * - 外挂文件 → 先落到应用缓存再作 subtitle slave 挂载；
     * - 关闭 → `setSpuTrack(-1)`。
     *
     * **`None` 也必须显式处理**：去掉 `no-spu` 之后 libVLC 会按自己的语言偏好自动挑一条
     * 内嵌字幕轨；用户没选主字幕时那条会凭空出现，和自绘的次字幕叠在一起。
     */
    private fun applyPrimaryToPlayer(source: SubtitleSource): Job? = when (source) {
        SubtitleSource.None -> {
            controller.selectSubtitleTrack(-1)
            updateTrack(forPrimary = true) { it.copy(isLoading = false) }
            null
        }

        is SubtitleSource.EmbeddedTrack -> {
            val ok = controller.selectSubtitleTrack(source.trackIndex)
            updateTrack(forPrimary = true) {
                it.copy(isLoading = false, error = if (ok) null else "libVLC 选不中该字幕轨")
            }
            null
        }

        is SubtitleSource.ExternalFile -> subtitleScope.launch {
            val path = withContext(Dispatchers.IO) { stageSubtitleFile(source) }
            val ok = path != null && controller.addSubtitleFile(path)
            updateTrack(forPrimary = true) {
                it.copy(isLoading = false, error = if (ok) null else "libVLC 加载不了该字幕文件")
            }
        }
    }

    /**
     * 把外挂字幕文件复制到应用缓存目录，返回本地路径。
     *
     * libVLC 的 subtitle slave 只认它自己能打开的路径 / MRL，**不认 `content://`**
     * （SAF 返回的正是这个），直接传会静默失败。字幕文件很小，放 cacheDir 由系统回收。
     */
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

    private fun refreshEmbeddedWindow(position: Long) {
        if (!tracksReady || released) return
        // 只有次字幕还在自绘链路里 —— 主字幕已交给 libVLC，不参与这里的窗口读取。
        val selected = setOfNotNull((_secondary.value.source as? SubtitleSource.EmbeddedTrack)?.trackIndex)
        embeddedCues.request(selected, position)
        publishEmbeddedState(embeddedCues.state.value)
    }

    private fun publishEmbeddedState(state: EmbeddedCueState) {
        updateTrack(forPrimary = false) { track ->
            val source = track.source as? SubtitleSource.EmbeddedTrack
            if (source == null || source.trackIndex !in state.tracks) track else track.copy(
                cues = state.cues[source.trackIndex].orEmpty(),
                isLoading = state.isLoading, error = state.error
            )
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
                Log.w(TAG, "读取内嵌字幕轨失败", error)
                _embeddedTrackStatus.value = "读取失败：${error.message}"
                _notice.value = "读取内嵌字幕轨失败：${error.message}"
                failEmbeddedTracks("读取字幕轨失败：${error.message}")
            }
        }
    }

    fun retryEmbeddedTracks() { if (!released) loadEmbeddedTrackList() }

    private fun failEmbeddedTracks(message: String) {
        for (primary in listOf(true, false)) updateTrack(primary) { track ->
            if (track.source is SubtitleSource.EmbeddedTrack) track.copy(isLoading = false, error = message)
            else track
        }
    }

    private fun trySelectDefaultSubtitle() {
        if (released || !sourcesRestored || !needDefaultSubtitle) return
        val tracks = _embeddedTracks.value.filterNot { it.isBitmap }
        val preferred = tracks.firstOrNull {
            val language = it.language?.lowercase().orEmpty()
            language.startsWith("zh") || language == "chi" || language == "zho"
        } ?: tracks.firstOrNull() ?: return
        needDefaultSubtitle = false
        selectEmbedded(preferred, forPrimary = true)
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
     * 把 libVLC 当前选中的字幕轨纠正回「我们记录的主字幕源」。
     *
     * 去掉 `no-spu` 之后 VLC 会自己按语言偏好挑一条内嵌字幕轨。只要它挑的不是用户选的
     * （尤其是用户根本没选主字幕的情形），这里纠正回去。
     *
     * 外挂文件不在此列：它由 `addSlave(..., select = true)` 自己接管，硬设 `-1`
     * 反而会把刚挂上的字幕关掉。
     */
    private fun enforcePrimarySubtitleSelection() {
        val want = when (val source = _primary.value.source) {
            SubtitleSource.None -> -1
            is SubtitleSource.EmbeddedTrack -> source.trackIndex
            is SubtitleSource.ExternalFile -> return
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
            _notice.value = "已把播放器音量从 0 调到 100%（遥控音量键调的是电视音量，不控播放器）"
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
            // 临时诊断用的「上一条已打过日志的字幕」
            var lastLoggedCue: SubtitleCue? = null
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
                val primaryCue = _primary.value.cueAt(position)
                _secondaryCue.value = _secondary.value.cueAt(position)
                _primaryCue.value = primaryCue

                // 临时诊断：字幕切换时打一行，确认「播放位置」与「字幕时间轴」是否对齐。
                // 只在变化时打，不会刷屏。若这里一直在打「无」而 cues 不为 0，
                // 就说明两套时间的基准不一致；若 cues 就是 0，说明读取那步没成。
                if (primaryCue != lastLoggedCue) {
                    lastLoggedCue = primaryCue
                    Log.i(
                        TAG,
                        "字幕@${position}ms 主=" + (
                            primaryCue?.let { "[${it.startMs}-${it.endMs}] ${it.text.take(40)}" }
                                ?: "无"
                            ) + "（本路共 ${_primary.value.cues.size} 条，来源 ${_primary.value.source::class.simpleName}）"
                    )
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

        /** 收到 libVLC 报错后等这么久再判定，以便拿到轨道信息写进详情。 */
        const val ERROR_SETTLE_DELAY_MS = 1200L

        const val TAG = "DualSubTV"
    }
}
