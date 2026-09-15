package com.dualsub.tv.subtitle

/**
 * 时间戳解析。
 *
 * 同时兼容 SRT/VTT 的毫秒写法与 ASS 的厘秒写法：
 * 小数部分一律右补零到 3 位，于是
 * `00:00:01,5` → 500ms，
 * `0:00:01.50` → 500ms（ASS 两位厘秒 = 50 厘秒），
 * `0:00:01.05` → 50ms。
 *
 * `HH:MM:SS` 与 `MM:SS` 两种段数都被接受，因为 WebVTT 允许省略小时段。
 * 解析失败返回 null，由调用方决定跳过该条目还是回退。
 */
internal object SubtitleTime {

    private val TIMESTAMP = Regex("""^(?:(\d{1,3}):)?(\d{1,3}):(\d{1,3})[.,](\d{1,3})$""")

    fun parse(raw: String): Long? {
        val match = TIMESTAMP.matchEntire(raw.trim()) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millis = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: return null
        // 分钟/秒超出 59 时不做额外校正，按位权相加即可得到正确时刻。
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }
}
