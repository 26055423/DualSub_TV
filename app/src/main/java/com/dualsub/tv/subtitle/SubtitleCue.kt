package com.dualsub.tv.subtitle

/**
 * 一条字幕。时间轴统一使用毫秒，与 `Player.currentPosition` 同一单位，
 * 便于直接做二分查找定位当前字幕。
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    /** 已做换行展开与标签清理的纯文本，可能含 `\n`。 */
    val text: String,
    /** ASS 特效参数；SRT/VTT 格式永远为 null。 */
    val assOverride: AssOverride? = null
) {
    /** 该字幕在 [positionMs] 时刻是否可见（闭区间，容忍 0 长度）。 */
    fun isVisibleAt(positionMs: Long): Boolean =
        positionMs >= startMs && positionMs <= endMs
}
