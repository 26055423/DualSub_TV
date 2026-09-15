package com.dualsub.tv.subtitle

/**
 * ASS / SSA 解析器。
 *
 * 只取时间轴与文本，样式与特效（`{\pos}`、`{\an8}`、卡拉OK 等）会被剥离 ——
 * 当前版本不做 ASS 特效还原，这是相对 mpv 方案明确接受的取舍。
 *
 * 关键容错点：`Text` 字段本身可以包含逗号，因此必须按 `Format:` 声明的字段数
 * 限制切分次数，否则文本会被截断。
 */
object AssParser : SubtitleParser {

    override val format = SubtitleFormat.ASS

    /** 未声明 `Format:` 时的 ASS 默认字段顺序。 */
    private val DEFAULT_FIELDS = listOf(
        "layer", "start", "end", "style", "name",
        "marginl", "marginr", "marginv", "effect", "text"
    )

    private val ASS_LINE_BREAK = Regex("""\\[Nn]""")
    private val ASS_HARD_SPACE = Regex("""\\h""")

    override fun parse(content: String): List<SubtitleCue> {
        val lines = SubtitleTextCleaner.normalizeNewlines(content).lines()
        val cues = ArrayList<SubtitleCue>()
        var inEventsSection = false
        var fields = DEFAULT_FIELDS

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[")) {
                inEventsSection = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEventsSection) continue

            when {
                line.startsWith("Format:", ignoreCase = true) -> {
                    val declared = line.substringAfter(':')
                        .split(',')
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                    if ("text" in declared && "start" in declared) fields = declared
                }

                line.startsWith("Dialogue:", ignoreCase = true) -> {
                    parseDialogue(line.substringAfter(':').trim(), fields)?.let { cues += it }
                }
            }
        }
        return cues.sortedAndDistinct()
    }

    private fun parseDialogue(body: String, fields: List<String>): SubtitleCue? {
        val textIndex = fields.indexOf("text").takeIf { it >= 0 } ?: return null
        val startIndex = fields.indexOf("start").takeIf { it >= 0 } ?: return null
        val endIndex = fields.indexOf("end").takeIf { it >= 0 } ?: return null

        // 限制切分次数，让最后一段（通常是 Text）保留自身的逗号
        val parts = body.split(',', limit = fields.size)
        if (parts.size <= textIndex || parts.size <= startIndex || parts.size <= endIndex) return null

        val startMs = SubtitleTime.parse(parts[startIndex]) ?: return null
        val endMs = SubtitleTime.parse(parts[endIndex]) ?: return null

        val text = cleanText(parts[textIndex])
        if (text.isEmpty()) return null

        return SubtitleCue(startMs, endMs, text)
    }

    private fun cleanText(raw: String): String {
        val withBreaks = ASS_HARD_SPACE.replace(
            ASS_LINE_BREAK.replace(raw, "\n"),
            "\u00A0"
        )
        return SubtitleTextCleaner.tidy(
            SubtitleTextCleaner.stripAssOverrides(withBreaks)
        )
    }
}
