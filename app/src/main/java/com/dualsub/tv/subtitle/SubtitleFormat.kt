package com.dualsub.tv.subtitle

/**
 * 支持的字幕格式。
 *
 * 本包（字幕解析）刻意不依赖 androidx.media3：MIME 常量在此以字面量书写，
 * 既方便用纯 JVM 单元测试覆盖，也避免解析层与播放引擎耦合。
 */
enum class SubtitleFormat(
    val displayName: String,
    /** 不含点号的扩展名，均为小写。 */
    val extensions: Set<String>
) {
    SRT("SubRip (.srt)", setOf("srt")),
    VTT("WebVTT (.vtt)", setOf("vtt", "webvtt")),
    ASS("ASS/SSA (.ass)", setOf("ass", "ssa")),
    UNKNOWN("未知格式", emptySet());

    companion object {
        /** 视频内嵌字幕轨的 SRT MIME。 */
        const val MIME_SUBRIP = "application/x-subrip"
        const val MIME_SUBVIEWER = "application/x-subviewer"
        const val MIME_VTT = "text/vtt"
        const val MIME_SSA = "text/x-ssa"

        /** 按文件名/路径后缀判断格式。 */
        fun fromFileName(fileName: String?): SubtitleFormat {
            val ext = fileName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.trim()
            if (ext.isNullOrEmpty()) return UNKNOWN
            return entries.firstOrNull { ext in it.extensions } ?: UNKNOWN
        }

        /** 按媒体 MIME 判断格式（用于视频内嵌字幕轨）。 */
        fun fromMimeType(mimeType: String?): SubtitleFormat =
            when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
                MIME_SUBRIP,
                MIME_SUBVIEWER -> SRT

                MIME_VTT,
                "text/webvtt" -> VTT

                MIME_SSA,
                "text/x-ass",
                "application/x-ssa",
                "application/x-ass" -> ASS

                else -> UNKNOWN
            }
    }
}
