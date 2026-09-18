package com.dualsub.tv.core

import android.util.Log

/**
 * 应用内的日志收集器，用来把 logcat 里的关键行**显示在电视画面上**。
 *
 * **为什么需要它**
 *
 * libVLC 的音频 / 解码错误只打进 logcat（tag 是 `VLC`、`VLC-std`），而真机上抓 logcat
 * 要先开网络调试、再连电脑，一轮下来很折腾 —— 尤其是当「有没有声音」这种问题只能靠
 * 现场观察时。
 *
 * **为什么能读到**
 *
 * Android 从 4.1 起允许应用读取**自己进程**的日志（不需要 READ_LOGS 权限），而 libVLC
 * 是以 native 库的形式跑在我们自己的进程里，所以那些 `E/VLC` 行我们读得到。
 *
 * 实现上是起一个 `logcat` 子进程持续读，在 Java 侧按关键字过滤，只保留最近
 * [MAX_LINES] 行（环形缓冲），界面按需取快照。
 */
object InAppLog {

    private const val TAG = "DualSubTV"

    /** 只保留最近这么多行，避免长时间播放把内存吃掉。 */
    private const val MAX_LINES = 400

    /** 我们关心的行 —— 够覆盖 libVLC、音频栈、崩溃。 */
    private val KEEP = Regex(
        "VLC|libvlc|aout|audio|Audio|decoder|AndroidRuntime|DualSubTV|MediaCodec.*(error|fail|Error|Fail|cannot|unsupport)"
    )

    /**
     * 高频刷屏行，必须丢掉。
     *
     * `MediaCodec` 每秒会打好几行 `[QIB]V, QIB:25/s, QIB num:xxxx` / `[ROB]V, ...`，
     * 它们本身没有诊断价值，却会把真正有用的音频日志挤出屏幕 —— 上一轮真机日志里
     * 满屏都是这些，音频信息一条都看不到。
     */
    /**
     * 要丢掉的噪音行。
     *
     * 这些行**恰好能匹配 [KEEP]**（含 `VLC` / `decoder` 等词），但它们是高频且无信息量的：
     * - `Exception occurred in MediaCodecInfo.getCapabilitiesForType`：每解析一条轨道打一次，
     *   一个带 38 条字幕轨的 MKV 就能刷几十行；
     * - `window: request N not implemented`、`can't get Subtitles Surface`、
     *   `cannot blend subtitles with an opaque surface`：都是我们**故意**不给 VLC 字幕 Surface
     *   造成的（双字幕由我们自己的叠加层绘制），属于预期内的无害输出；
     * - `Could not initialize NativeWindow Priv API`：同上，且各机型普遍出现。
     *
     * 不拦掉它们的话，真正有用的行（例如「内嵌字幕轨 N 条」「字幕交付 N 条」）会被挤出
     * 屏上只显示最后 60 行的窗口，等于日志白开了。
     */
    private val NOISE = Regex(
        "\\[QIB\\]|\\[ROB\\]|num:\\d+" +
            "|Exception occurred in MediaCodecInfo" +
            "|window: request \\d+ not implemented" +
            "|Could not initialize NativeWindow Priv API" +
            "|can't get Subtitles Surface" +
            "|cannot blend subtitles"
    )

    private val lines = ArrayDeque<String>()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 开始收集（幂等，重复调用无副作用）。
     *
     * 从 `Application.onCreate` 调一次即可 —— 越早开始，越不容易漏掉启动阶段的报错。
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        Thread({
            runCatching {
                // 读本进程日志不需要任何权限；-v time 让每行自带时间戳便于对照。
                val process = ProcessBuilder("logcat", "-v", "time")
                    .redirectErrorStream(true)
                    .start()

                process.inputStream.bufferedReader().useLines { sequence ->
                    sequence.forEach { line ->
                        if (!NOISE.containsMatchIn(line) && KEEP.containsMatchIn(line)) {
                            synchronized(lines) {
                                lines.addLast(line)
                                while (lines.size > MAX_LINES) lines.removeFirst()
                            }
                        }
                    }
                }
            }.onFailure {
                Log.w(TAG, "应用内日志收集不可用：${it.message}")
                synchronized(lines) { lines.addLast("（应用内日志不可用：${it.message}）") }
            }
        }, "in-app-logcat").apply {
            isDaemon = true
            start()
        }
    }

    /** 取一份当前日志快照（用于界面渲染）。 */
    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() {
        synchronized(lines) { lines.clear() }
    }
}
