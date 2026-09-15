package com.dualsub.tv.subtitle

/**
 * SubRip (.srt) 解析器。
 *
 * 容错点：
 * - 序号行缺失、时间行前后有多余空白、CRLF/CR 混用；
 * - 时间行后可跟老式坐标 `X1:.. X2:.. Y1:.. Y2:..`；
 * - 正文里的 `<i>`/`<font>` 等标签与 HTML 实体。
 */
object SrtParser : SubtitleParser {

    override val format = SubtitleFormat.SRT

    private const val ARROW = "-->"
    private val BLOCK_SEPARATOR = Regex("""\n[ \t]*\n""")
    private val WHITESPACE = Regex("""\s+""")

    override fun parse(content: String): List<SubtitleCue> {
        val blocks = SubtitleTextCleaner.normalizeNewlines(content).split(BLOCK_SEPARATOR)
        val cues = ArrayList<SubtitleCue>(blocks.size)

        for (block in blocks) {
            val lines = block.split('\n')
            val timeIndex = lines.indexOfFirst { it.contains(ARROW) }
            if (timeIndex < 0) continue

            val range = parseTimeLine(lines[timeIndex]) ?: continue
            val body = lines.subList(timeIndex + 1, lines.size).joinToString("\n")
            val text = cleanText(body)
            if (text.isEmpty()) continue

            cues += SubtitleCue(range.first, range.second, text)
        }
        return cues.sortedAndDistinct()
    }

    private fun parseTimeLine(line: String): Pair<Long, Long>? {
        val halves = line.split(ARROW)
        if (halves.size < 2) return null
        val startMs = SubtitleTime.parse(halves[0]) ?: return null
        val endToken = halves[1].trim().split(WHITESPACE).firstOrNull() ?: return null
        val endMs = SubtitleTime.parse(endToken) ?: return null
        return startMs to endMs
    }

    private fun cleanText(raw: String): String = SubtitleTextCleaner.tidy(
        SubtitleTextCleaner.unescapeHtmlEntities(
            SubtitleTextCleaner.stripHtmlTags(raw)
        )
    )
}
