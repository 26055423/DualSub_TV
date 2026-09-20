package com.dualsub.tv.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/** 一条音轨的详细信息（来自 `IMedia.tracks`，比 `TrackDescription` 丰富得多）。 */
data class AudioTrackDetail(
    val id: Int,
    /** 编码名，如 `aac` / `ac3` / `dts` / `truehd`。 */
    val codec: String?,
    val language: String?,
    val description: String?,
    val channels: Int
) {
    /** 菜单里的显示名，例如 `dts 6ch · chi`。 */
    fun label(): String = buildString {
        append(codec?.takeIf { it.isNotBlank() } ?: "未知编码")
        if (channels > 0) append(' ').append(channels).append("ch")
        language?.takeIf { it.isNotBlank() && it != "und" }?.let { append(" · ").append(it) }
    }
}

/** 一条内嵌字幕轨的摘要（来自 `IMedia.SubtitleTrack`）。 */
data class SubtitleTrackDetail(
    val id: Int,
    val language: String?,
    val description: String?
) {
    /** 菜单里的显示名，例如 `chi · 简体中文`。 */
    fun label(): String = buildString {
        val lang = language?.takeIf { it.isNotBlank() && it != "und" }
        append(lang ?: "未知语言")
        description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
}

/**
 * libVLC 播放器封装。
 *
 * **为什么从 Media3/ExoPlayer 换到 libVLC**
 *
 * 起因是 RM / RMVB 播不了。这类格式在 Media3 架构下是死路：Media3 没有 RealMedia 的
 * Extractor（已实地反编译 media3-extractor 1.4.1 的 26 个 Extractor 确认过，没有 rm），
 * 而 Media3 的 FFmpeg 扩展（`decoder_ffmpeg`）**只提供解码器、不解析容器** ——
 * 所以就算自己写一个 Extractor 把容器解出来，拿到的 RealVideo 码流也没有任何电视芯片
 * 能硬解，会重新卡死。libVLC 自带完整 FFmpeg，容器解析与软解码两样都有。
 *
 * 文本主、次字幕由 Compose 自绘，复用应用的字幕解析与系统字体。
 * VLC 字幕 Surface 用于自绘层不支持的图片主字幕（PGS / DVD SPU 等）。
 *
 * **硬件解码策略**
 *
 * `setHWDecoderEnabled(true, false)` 让 VLC 优先尝试硬解，失败时它会自行回退软解 ——
 * 这正是 RMVB 这类只有软解才能播的格式能出画面的关键。
 */
class VlcPlayerController(context: Context, cachingMs: Int = 1500) {

    private val appContext = context.applicationContext

    // **必须传「可变」List**：LibVLC 的构造器会往传进来的 list 里 add()
    // （反编译栈显示 LibVLC.java:76 → AbstractList.add → UnsupportedOperationException），
    // 所以不能直接传 listOf(...) 生成的不可变 List —— 那会在打开视频的瞬间崩掉整个 APP。
    // 这里复制一份 ArrayList 再传。
    //
    private val libVlc = LibVLC(
        appContext,
        ArrayList(buildLaunchOptions(cachingMs))
    )

    val mediaPlayer = MediaPlayer(libVlc)

    /**
     * 当前媒体的引用。
     *
     * 保留它是为了读 `IMedia.tracks` —— 那里有每条音轨的**编码名**、声道数和语言，
     * 而 `MediaPlayer.audioTracks` 只给 id 和名字，不足以判断「哪条音轨电视解得出声」。
     * 引用在 [release] 里释放。
     */
    private var currentMedia: Media? = null

    /**
     * `content://` 走文件描述符构造 Media 时，这个 fd 必须保持打开到播放结束，
     * 否则 libVLC 读到一半会失败。换文件与 release() 时关闭。
     */
    private var openedPfd: ParcelFileDescriptor? = null

    /** 播放状态变化：(是否在播放, 是否在缓冲)。 */
    var onState: ((isPlaying: Boolean, isBuffering: Boolean) -> Unit)? = null

    /** 时长变化。 */
    var onLength: ((lengthMs: Long) -> Unit)? = null

    /** 轨道信息发生变化（用于诊断层显示容器/编码）。 */
    var onTracks: ((description: String) -> Unit)? = null

    /** 播放出错。libVLC 的错误信息很少，所以这里只给一个信号，具体原因由上层结合容器/编码判断。 */
    var onError: ((message: String) -> Unit)? = null

    /** 播放自然结束。 */
    var onEndReached: (() -> Unit)? = null

    /** 倍速变化（菜单里改完通知界面刷新显示）。 */
    var onRateChanged: ((rate: Float) -> Unit)? = null

    private var released = false

    // One-shot seek target set by open(); consumed on the first Playing event so libVLC
    // has already parsed the container before we seek (setting mediaPlayer.time before
    // the first Playing event is silently ignored by libVLC).
    @Volatile private var pendingStartPositionMs = 0L

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    val seekTarget = pendingStartPositionMs
                    if (seekTarget > 0L) {
                        pendingStartPositionMs = 0L
                        mediaPlayer.time = seekTarget
                    }
                    onState?.invoke(true, false)
                }

                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped ->
                    onState?.invoke(false, false)

                MediaPlayer.Event.Buffering -> {
                    val percent = event.buffering
                    onState?.invoke(mediaPlayer.isPlaying, percent > 0f && percent < 100f)
                }

                MediaPlayer.Event.LengthChanged ->
                    onLength?.invoke(event.lengthChanged)

                MediaPlayer.Event.Vout, MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected ->
                    onTracks?.invoke(describeTracks())

                MediaPlayer.Event.EndReached ->
                    onEndReached?.invoke()

                MediaPlayer.Event.EncounteredError -> {
                    Log.w(TAG, "libVLC 报告播放错误")
                    onError?.invoke("libVLC 报告播放错误")
                }
            }
        }
    }

    // ---------------------------------------------------------------- 视频画布

    fun attachViews(layout: VLCVideoLayout) {
        if (released) return
        // 参数：视频容器 / DisplayManager（null 表示用默认显示）/ **是否启用字幕 Surface** / 是否用 TextureView
        //
        // 第三个参数必须是 true：主字幕由 libVLC 的 libass 渲染到它开出来的
        // `SubtitlesSurfaceView` 上。关掉它 VLC 根本不解字幕，主字幕会整条消失。
        // 次字幕与此无关 —— 它画在 Compose 层，位于 VLCVideoLayout 之上。
        runCatching { mediaPlayer.attachViews(layout, null, true, false) }
            .onFailure { Log.w(TAG, "attachViews 失败：${it.message}") }
    }

    fun detachViews() {
        runCatching { mediaPlayer.detachViews() }
    }

    // ---------------------------------------------------------------- 播放控制

    /**
     * 打开一个媒体。
     *
     * [options] 用于传媒体级选项，例如 SMB 的账号密码。
     */
    fun open(uri: Uri, startPositionMs: Long = 0L, options: List<String> = emptyList()) {
        if (released) return
        Log.i(TAG, "libVLC 打开：$uri  选项=${options.size} 项")

        // **content:// 必须走文件描述符**：libVLC 不认识 Android 媒体库的 content URI，
        // 直接传会得到 "Your input can't be opened / VLC is unable to open the MRL"。
        // Media3 是认 content:// 的，所以换引擎时这一点很容易漏（SMB 能播会掩盖它）。
        val media: Media = if (uri.scheme == "content") {
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("打不开该媒体库条目：$uri")
            runCatching { openedPfd?.close() }
            openedPfd = pfd
            Media(libVlc, pfd.fileDescriptor)
        } else {
            Media(libVlc, uri)
        }
        // 优先硬解；解不动时 libVLC 自行回退软解，这是 RMVB 能出画面的关键
        media.setHWDecoderEnabled(true, false)
        options.forEach { media.addOption(it) }
        mediaPlayer.media = media
        // 保留引用供读轨道用；此处**不** release（在 release() 里统一释放）
        currentMedia?.release()
        currentMedia = media
        pendingStartPositionMs = startPositionMs
        mediaPlayer.play()
    }

    fun togglePlayPause() {
        if (released) return
        runCatching {
            if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        }
    }

    fun pause() {
        if (released) return
        runCatching { mediaPlayer.pause() }
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val length = durationMs()
        val target = if (length > 0L) positionMs.coerceIn(0L, length) else positionMs.coerceAtLeast(0L)
        // Fast keyframe seek avoids decoding every intervening HEVC frame to an exact timestamp.
        runCatching { mediaPlayer.setTime(target, false) }
    }

    fun positionMs(): Long = if (released) 0L else runCatching { mediaPlayer.time }.getOrDefault(0L)

    fun durationMs(): Long = if (released) 0L else runCatching { mediaPlayer.length }.getOrDefault(0L)

    fun isPlaying(): Boolean = !released && runCatching { mediaPlayer.isPlaying }.getOrDefault(false)

    fun isSeekable(): Boolean = !released && runCatching { mediaPlayer.isSeekable }.getOrDefault(false)

    // ---------------------------------------------------------------- 倍速

    /** 当前播放速率（1.0 = 原速）。 */
    fun rate(): Float = if (released) 1f else runCatching { mediaPlayer.rate }.getOrDefault(1f)

    /** 设置播放速率。（原先还开了 `--audio-time-stretch` 保音调，已移除，见 companion 注释。） */
    fun setRate(value: Float) {
        if (released) return
        val clamped = value.coerceIn(0.25f, 4f)
        runCatching { mediaPlayer.rate = clamped }
            .onSuccess { onRateChanged?.invoke(clamped) }
    }

    // ---------------------------------------------------------------- 音量

    /** 当前音量（0-100）。 */
    fun volume(): Int = if (released) 0 else runCatching { mediaPlayer.volume }.getOrDefault(0)

    fun setVolume(value: Int) {
        if (released) return
        runCatching { mediaPlayer.volume = value.coerceIn(0, 100) }
    }

    // ---------------------------------------------------------------- 音轨

    /** 当前音轨数量。 */
    fun audioTrackCount(): Int = if (released) 0 else runCatching { mediaPlayer.audioTracksCount }.getOrDefault(0)

    /** 当前选中的音轨 id；-1 表示「无音轨」被选中。 */
    fun currentAudioTrackId(): Int = if (released) -1 else runCatching { mediaPlayer.audioTrack }.getOrDefault(-1)

    /**
     * 全部音频轨的详细信息，带编码名与声道数。
     *
     * 用 `IMedia.tracks` 而不是 `MediaPlayer.audioTracks` —— 后者只有 id 和名字，
     * 判断不出「哪条电视解得出声」。`IMedia.AudioTrack` 是音频轨独有的类型，
     * 所以直接用它做类型判定，不必依赖轨道类型常量。
     */
    fun audioTrackDetails(): List<AudioTrackDetail> {
        if (released) return emptyList()
        val media = currentMedia ?: return emptyList()
        return runCatching {
            (0 until media.trackCount).mapNotNull { index ->
                val track = media.getTrack(index) ?: return@mapNotNull null
                val audio = track as? IMedia.AudioTrack ?: return@mapNotNull null
                AudioTrackDetail(
                    id = track.id,
                    codec = track.codec,
                    language = track.language,
                    description = track.description,
                    channels = audio.channels
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "读取音轨详情失败：${error.message}")
            emptyList()
        }
    }

    /**
     * 可选的音轨列表：`(id, 显示名)`，显示名里带上编码与声道，例如 `ac3 6ch · chi`。
     */
    fun audioTrackOptions(): List<Pair<Int, String>> {
        if (released) return emptyList()

        val details = audioTrackDetails()
        if (details.isNotEmpty()) {
            return details.map { it.id to it.label() }
        }
        // 退化路径：拿不到 IMedia 轨道时，用 MediaPlayer 的名字
        return runCatching {
            mediaPlayer.audioTracks.orEmpty().map { track ->
                track.id to (track.name?.takeIf { it.isNotBlank() } ?: "音轨 ${track.id}")
            }
        }.getOrDefault(emptyList())
    }

    /** 选中指定音轨。用于「默认选到的音轨解不了，手动切一条」的场景。 */
    fun selectAudioTrack(id: Int): Boolean =
        if (released) false else runCatching { mediaPlayer.setAudioTrack(id) }.getOrDefault(false)

    /**
     * 自动挑一条音轨，返回一段说明；null 表示无需改动。
     *
     * **为什么需要这个**：MKV 常把 DTS-HD / TrueHD 这类只能软解的音轨放在第一位，
     * VLC 按文件顺序默认选中它；一旦软解链路有问题，表现就是「有画面没声音」。
     * 而文件里往往同时带了一条 AC3 / AAC 轨，那条是电视能直接出的。
     *
     * 判断依据是 `IMedia.tracks` 里的**编码名**（不是猜的）：通用的 AAC / AC3 / MP3 /
     * FLAC / Opus 给高分，需要软解的 DTS / TrueHD / MLP 给低分，声道数略作加权。
     *
     * **四级策略**，从高到低依次判断：
     * 1. 已经挂在最优轨上 → 不动；
     * 2. 一条音轨都没挂上 → 挂上最优那条；
     * 3. 最优明显更通用（分差 ≥ 10，例如当前是 DTS 而文件里另有 AC3）→ 切过去；
     * 4. **各条音轨编码同档**（例如全是 DTS / TrueHD）→ **退回第一条兜底**。
     *
     * 第 4 条是关键：**不能因为「选不出更好的」就卡着不选** —— 那样整个文件一条音轨都不会
     * 被挂上，结果是彻底没声音。宁可先挂上第一条，用户再用菜单手动换。
     */
    fun autoSelectBestAudioTrack(): String? {
        if (released) return null
        return runCatching {
            val details = audioTrackDetails()
            if (details.isEmpty()) return@runCatching null

            fun score(d: AudioTrackDetail) = codecScore(d.codec) + d.channels.coerceIn(0, 8)

            // **关键校验**：IMedia.Track.id 与 MediaPlayer.TrackDescription.id 并未保证是同一套编号。
            // 若拿 IMedia 的 id 去调 setAudioTrack()，可能切到不存在的轨，表现就是「有画面、完全没声音」。
            val validIds = mediaPlayer.audioTracks.orEmpty().map { it.id }.toSet()
            if (validIds.isEmpty()) return@runCatching null

            val currentId = mediaPlayer.audioTrack
            val current = details.firstOrNull { it.id == currentId }
            val best = details.filter { it.id in validIds }.maxByOrNull { score(it) }
                ?: return@runCatching null

            // 1) 已经挂在最优轨上，不动
            if (best.id == currentId) return@runCatching null

            // 2) 一条都没挂上 → 直接挂最优
            if (current == null) {
                // 只记日志、**不弹提示**：这是"兜底自动行为"，用户既没要求、也不需要知道
                // （真机反馈过：看片时被「原未挂载音轨，已选用…」这条提示条打扰）。
                if (mediaPlayer.setAudioTrack(best.id)) {
                    Log.i(TAG, "原本未挂载音轨，改用 ${best.codec}")
                }
                return@runCatching null
            }

            // 3) 最优明显更通用 → 切过去
            if (score(best) - score(current) >= 10) {
                return@runCatching if (mediaPlayer.setAudioTrack(best.id)) {
                    Log.i(TAG, "自动切换音轨：${current.codec} → ${best.codec}")
                    "已自动选用「${best.label()}」音轨（原音轨 ${current.codec ?: "未知"} 可能设备解不了）"
                } else {
                    null
                }
            }

            // 4) 各条音轨编码同档（全是 DTS / TrueHD 之类）→ 退回第一条兜底。
            //    关键是**不能因为「选不出更好的」就卡着不选**，否则一条都不会被挂上。
            val first = details.firstOrNull { it.id in validIds } ?: return@runCatching null
            if (first.id != currentId) {
                return@runCatching if (mediaPlayer.setAudioTrack(first.id)) {
                    Log.i(TAG, "音轨编码同档，改用第一条：${first.codec}")
                    "各音轨编码同档，已改用第一条「${first.label()}」"
                } else {
                    null
                }
            }

            null
        }.getOrElse { error ->
            Log.w(TAG, "自动选轨失败（不影响播放）：${error.message}")
            null
        }
    }

    /**
     * 音轨概览文本，例如 `音轨 2 条：[▶aac 2ch] [dts 6ch]`。
     *
     * MKV 常有多条音轨（TrueHD 5.1 + AC3 2.0 之类），默认选中那条如果设备/解码器吃不下，
     * 表现就是「有画面没声音」。所以这里把**全部**音轨都列出来，并用 ▶ 标出当前选中的那条。
     */
    fun describeAudioTracks(): String {
        if (released) return "尚未取得音轨"
        val currentId = runCatching { mediaPlayer.audioTrack }.getOrDefault(-1)

        val details = audioTrackDetails()
        if (details.isNotEmpty()) {
            val rendered = details.joinToString(" ") { d ->
                if (d.id == currentId) "[▶${d.label()}]" else "[${d.label()}]"
            }
            return "音轨 ${details.size} 条：$rendered"
        }

        return runCatching {
            val tracks = mediaPlayer.audioTracks
            if (tracks.isNullOrEmpty()) return "无音轨（或仍在解析）"
            val rendered = tracks.joinToString(" ") { track ->
                val name = track.name?.takeIf { it.isNotBlank() } ?: "轨道${track.id}"
                if (track.id == currentId) "[▶$name]" else "[$name]"
            }
            "音轨 ${tracks.size} 条：$rendered"
        }.getOrElse { "尚未取得音轨" }
    }

    // ---------------------------------------------------------------- 画面

    /**
     * 设置显示比例。
     *
     * [ratio] 传 null 表示回到「跟随视频原始比例」；否则是 `"16:9"` / `"4:3"` 这类字符串。
     */
    fun setAspectRatio(ratio: String?) {
        if (released) return
        runCatching { mediaPlayer.aspectRatio = ratio }
            .onFailure { Log.w(TAG, "设置显示比例失败：${it.message}") }
    }

    /** 当前显示比例；null 表示跟随原始比例。 */
    fun aspectRatio(): String? = if (released) null else runCatching { mediaPlayer.aspectRatio }.getOrNull()

    /** 视频宽高（用于详情面板）。 */
    fun videoSize(): Pair<Int, Int>? {
        if (released) return null
        return runCatching {
            mediaPlayer.currentVideoTrack?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width to it.height }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 诊断信息

    /**
     * 用当前轨道拼一行诊断文本。
     *
     * libVLC 报错时几乎不给细节，所以「当前解出来的到底是什么编码」就成了判断
     * 播不出来的主要依据 —— 这一行直接显示在屏上诊断层里。
     */
    fun describeTracks(): String {
        if (released) return "尚未取得轨道信息"
        return runCatching {
            val parts = ArrayList<String>()

            mediaPlayer.currentVideoTrack?.let { video ->
                parts += buildString {
                    append("视频=").append(video.codec?.takeIf { it.isNotBlank() } ?: "未知")
                    if (video.width > 0 && video.height > 0) {
                        append(' ').append(video.width).append('x').append(video.height)
                    }
                }
            }

            val details = audioTrackDetails()
            if (details.isNotEmpty()) {
                val currentId = mediaPlayer.audioTrack
                val current = details.firstOrNull { it.id == currentId }
                parts += "音频=" + (current?.label() ?: "无")
                if (details.size > 1) parts += "音轨共 ${details.size} 条"
            } else {
                val audioTracks = mediaPlayer.audioTracks
                if (audioTracks.isNullOrEmpty()) {
                    parts += "音频=无音轨"
                } else {
                    val currentId = mediaPlayer.audioTrack
                    audioTracks.firstOrNull { it.id == currentId }?.let { current ->
                        parts += "音频(当前)=" + (current.name?.takeIf { it.isNotBlank() } ?: "轨道${current.id}")
                    }
                    if (audioTracks.size > 1) parts += "音轨共 ${audioTracks.size} 条"
                }
            }

            if (parts.isEmpty()) "尚未取得轨道信息" else parts.joinToString("；")
        }.getOrElse { "尚未取得轨道信息" }
    }

    // ---------------------------------------------------------------- 字幕（主字幕）

    /**
     * 可选的内嵌字幕轨 —— 主字幕从这里选。
     *
     * **`id` 必须取自 `MediaPlayer.spuTracks`**：`setSpuTrack()` 只认那一套编号。
     * 拿 `IMedia.SubtitleTrack.id` 去选轨会直接返回 false —— 真机上的表现就是
     * 「⚠ libVLC 选不中该字幕轨」。这与音轨那边的坑同源（见 [autoSelectBestAudioTrack]：
     * `IMedia.Track.id` 与 `TrackDescription.id` 不是同一套编号）。
     *
     * 语言码只能从 `IMedia.SubtitleTrack` 侧取（`TrackDescription` 只有 id + name），
     * 两边**按容器顺序对齐** —— 它们都是按轨道顺序列出的。
     */
    fun subtitleTrackDetails(): List<SubtitleTrackDetail> {
        if (released) return emptyList()
        val available = mediaPlayer.spuTracks.orEmpty()
        if (available.isEmpty()) return emptyList()

        val rich = runCatching {
            val media = currentMedia ?: return@runCatching emptyList()
            (0 until media.trackCount).mapNotNull { index ->
                media.getTrack(index) as? IMedia.SubtitleTrack
            }
        }.getOrDefault(emptyList())

        return available.mapIndexed { index, track ->
            val meta = rich.getOrNull(index)
            SubtitleTrackDetail(
                id = track.id,
                language = meta?.language,
                description = track.name?.takeIf { it.isNotBlank() } ?: meta?.description
            )
        }
    }

    /** 内嵌字幕轨数量（诊断用）。 */
    fun spuTrackCount(): Int = if (released) 0 else runCatching { mediaPlayer.spuTracksCount }.getOrDefault(0)

    /** 当前主字幕轨道 id；`-1` 表示主字幕未启用。 */
    fun currentSubtitleTrackId(): Int =
        if (released) -1 else runCatching { mediaPlayer.spuTrack }.getOrDefault(-1)

    /**
     * 选主字幕轨道（内嵌）。传 `-1` 关闭主字幕。
     *
     * **没选字幕时也必须显式调一次 `-1`**：去掉 `no-spu` 之后 libVLC 会按自己的
     * 语言偏好自动挑一条内嵌字幕轨，那会是一条「凭空冒出来的字幕」，
     * 和自绘的次字幕叠在一起。
     */
    fun selectSubtitleTrack(id: Int): Boolean =
        if (released) false else runCatching { mediaPlayer.setSpuTrack(id) }.getOrDefault(false)

    /** 主字幕时间偏移，单位毫秒（正数 = 字幕延后出现）。 */
    fun subtitleDelayMs(): Long =
        if (released) 0L else runCatching { mediaPlayer.spuDelay / 1000L }.getOrDefault(0L)

    /** 设置主字幕时间偏移（毫秒）。libVLC 底层单位是微秒，这里换算。 */
    fun setSubtitleDelayMs(ms: Long): Boolean =
        if (released) false else runCatching { mediaPlayer.setSpuDelay(ms * 1000L) }.getOrDefault(false)

    /**
     * 播放中挂载外挂字幕文件作为主字幕。
     *
     * [path] **必须是本地文件路径**：libVLC 的 slave 走的是它自己的协议层，
     * 不认 `content://`（SAF 返回的就是这个），传进去只会静默失败。
     * 调用方负责先把文件落到应用缓存目录。
     */
    fun addSubtitleFile(path: String, select: Boolean = true): Boolean {
        if (released) return false
        return runCatching {
            mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, path, select)
        }.getOrDefault(false)
    }

    /**
     * 流统计文本（码率、丢帧、损坏包），供「按上/下显示的播放信息」用。
     *
     * 取的是 libVLC 的 `IMedia.Stats`。注意它**只在播放过程中才有意义**，
     * 刚打开还没起播时会全是 0，所以这里对全 0 的情况直接返回 null，
     * 由界面显示「信息读取中…」而不是误导性的 `0 kbps`。
     */
    fun streamStatsText(): String? {
        if (released) return null
        return runCatching {
            val stats = mediaPlayer.media?.stats ?: return null
            val inputKbps = (stats.inputBitrate / 1000f).toInt()
            val demuxKbps = (stats.demuxBitrate / 1000f).toInt()
            if (inputKbps <= 0 && demuxKbps <= 0 && stats.decodedVideo == 0) return null

            buildString {
                if (inputKbps > 0) append("输入 ").append(inputKbps).append(" kbps")
                if (demuxKbps > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("解码 ").append(demuxKbps).append(" kbps")
                }
                val lost = stats.lostPictures
                append(" · 视频解码 ").append(stats.decodedVideo)
                append(" / 显示 ").append(stats.displayedPictures).append(" 帧")
                if (lost > 0) append(" · 丢帧 ").append(lost)
                val corrupt = stats.demuxCorrupted
                if (corrupt > 0) append(" · 损坏 ").append(corrupt)
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /**
     * 当前输入速率（**字节/秒**），供播放界面右上角的「实时网速」用。
     *
     * 取的是 libVLC 的 `input_bitrate`（bit/s）除以 8 —— 当贝那类播放器显示的 `kB/s`
     * 就是字节速率。**刚起播还没解封装、或暂停后不再读盘时为 null**，界面据此不显示，
     * 而不是摆一个误导性的 `0.0 kB/s`。
     */
    fun inputBytesPerSec(): Long? {
        if (released) return null
        return runCatching {
            val bitrate = mediaPlayer.media?.stats?.inputBitrate ?: return null
            if (bitrate <= 0f) null else (bitrate / 8f).toLong()
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 释放

    fun release() {
        if (released) return
        released = true
        Log.i(TAG, "释放 libVLC 播放器")
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.setEventListener(null) }
        runCatching { mediaPlayer.release() }
        runCatching { currentMedia?.release() }
        currentMedia = null
        runCatching { openedPfd?.close() }
        openedPfd = null
        runCatching { libVlc.release() }
    }

    companion object {
        const val TAG = "DualSubTV"

        /**
         * 传给 LibVLC 的启动参数。
         *
         * 提成公开常量是为了能在界面上显示出来 —— 真机来回试的时候，最耗时间的其实是
         * 「确认装的是哪一版」，把它打在屏上就不用猜了。
         *
         * 各参数的来由：
         * - `network-caching` / `file-caching`：SMB 片源给足缓冲，否则频繁卡顿。
         * - 保留 VLC 默认的丢迟到帧策略，避免快进或硬解追不上时继续堆积过期帧。
         * - `no-sub-autodetect-file`：禁止 VLC 自动加载「与视频同名的字幕文件」。
         *   主字幕由用户在界面里显式选（内嵌轨或外挂文件），自动加载会抢掉这个选择。
         *   保留 VLC 字幕能力，用于图片主字幕；文本主、次字幕由 Compose 自绘。
         * - `verbose=1`：保留警告和错误，减少播放时的调试日志。
         *   排查音频模块选择、无声或直通问题时临时调回 2（可查看 audio output: using module）。
         * - **不再设 `no-spdif`**：HDMI/SPDIF 直通不再被主动关掉。
         *
         *   **但「打开直通」在这台设备上并不等于拿到了直通** —— 见下面 `aout` 处的实测记录：
         *   `android_audiotrack`（唯一支持 IEC61937 的模块）在这台 TCL 上起不来，
         *   只能退回 OpenSL ES，而 OpenSL ES 只出 PCM，**结果仍是下混立体声**。
         *   真出现无声时，把 `"--no-spdif"` 加回下面列表。
         *
         * **已移除**：`audio-time-stretch`（变速时保音调）。它会介入 aout 初始化，
         * 而 `too low audio sample frequency (0)` 正是在那一步抛出的；代价是以后非原速播放
         * 音调会跟着变。等音频稳定后再考虑是否加回。
         *
         * **教训**：曾经用过 `--audio-channels=stereo` 和 `--aout=android_audiotrack`，
         * 前者因为取值不被接受，导致 LibVLC **构造期直接崩进程**（且是全局参数，MP4 也崩）。
         * 改全局启动参数前务必确认参数名与取值都合法。
         */
        val LAUNCH_OPTIONS: List<String> = listOf(
            "--network-caching=1500",
            "--file-caching=1500",
            "--no-sub-autodetect-file",
            // Keep warnings/errors; per-frame decoder diagnostics add work on TV hardware.
            "--verbose=1",
            // **音频输出模块：android_audiotrack 在这台电视上起不来，所以用 OpenSL ES。**
            //
            // 2026-09-17 真机实测（TCL，MTK）：换成 `android_audiotrack` 后**完全无声**，
            // 即 `too low audio sample frequency (0)` → `module not functional` 复现。
            // 当时怀疑它由 `--audio-time-stretch` 引起，**这个怀疑是错的** ——
            // 该选项早已移除，audiotrack 依然起不来。
            //
            // 换个说法：**libVLC 的 Android aout 在这台设备上只有 OpenSL ES 这一条路能出声。**
            //
            // 代价必须讲清楚：OpenSL ES 只出 PCM，**拿不到 IEC61937 直通**，所以 5.1 / Atmos
            // 一律被下混成立体声。当贝播放器能在这台电视上正常输出 Atmos，说明设备本身具备
            // 直通能力 —— 差距在 libVLC 这一侧，不在设备。要拿直通只能另想办法
            //（升级 libVLC 版本 / 换播放内核），不是改这一个参数能解决的。
            "--aout=opensles_android"
        )

        /** 组装 LibVLC 启动参数，替换用户设置的网络和文件缓冲时长。 */
        fun buildLaunchOptions(cachingMs: Int): List<String> {
            if (cachingMs == 1500) return LAUNCH_OPTIONS
            return LAUNCH_OPTIONS.map { opt ->
                when {
                    opt.startsWith("--network-caching=") -> "--network-caching=$cachingMs"
                    opt.startsWith("--file-caching=") -> "--file-caching=$cachingMs"
                    else -> opt
                }
            }
        }

        /**
         * 按编码给音轨打分，分越高越「肯定能出声」。
         *
         * 高分全是电视/芯片普遍支持的编码；低分是只能靠软件解码的
         * DTS / TrueHD / MLP —— 它们正是「有画面没声音」的常见来源。
         */
        fun codecScore(codec: String?): Int {
            val c = codec?.lowercase().orEmpty()
            return when {
                c.contains("aac") -> 100
                c.contains("ac3") || c.contains("eac3") -> 90
                c.contains("mp3") -> 85
                c.contains("flac") -> 80
                c.contains("opus") || c.contains("vorbis") -> 75
                c.contains("pcm") || c.contains("lpcm") -> 70
                c.contains("dts") || c.contains("dca") -> 20
                c.contains("truehd") || c.contains("mlp") -> 15
                c.isBlank() -> 50
                else -> 40
            }
        }
    }
}
