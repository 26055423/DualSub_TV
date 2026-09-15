package com.dualsub.tv.subtitle

/**
 * WebVTT (.vtt) 解析器。
 *
 * 容错点：
 * - 跳过 `WEBVTT` 头与 `NOTE`/`STYLE`/`REGION` 块；
 * - cue 标识行可有可无；
 * - 时间行可带 cue settings（`line:90% align:middle`）；
 * - 时间戳可以是 `MM:SS.mmm` 也可以是 `HH:MM:SS.mmm`；
 * - 正文里的 `<v Speaker>`、`<c.class>`、`<00:00:01.000>` 等标记。
 */
object VttParser : SubtitleParser {

    override val format = SubtitleFormat.VTT

    private const val ARROW = "-->"
    private val BLOCK_SEPARATOR = Regex("""\n[ \t]*\n""")
    private val WHITESPACE = Regex("""\s+""")
    private val VTT_TIMESTAMP_TAG = Regex("""<[\d:.]+>""")

    override fun parse(content: String): List<SubtitleCue> {
        val blocks = SubtitleTextCleaner.normalizeNewlines(content).split(BLOCK_SEPARATOR)
        val cues = ArrayList<SubtitleCue>(blocks.size)

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) continue

            val lines = trimmed.split('\n')
            val firstLine = lines.first().trim()
            if (firstLine.startsWith("NOTE") ||
                firstLine.startsWith("STYLE") ||
                firstLine.startsWith("REGION")
            ) {
                continue
            }

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
        // 右侧首段是结束时间，其后可能紧跟 cue settings
        val endToken = halves[1].trim().split(WHITESPACE).firstOrNull() ?: return null
        val endMs = SubtitleTime.parse(endToken) ?: return null
        return startMs to endMs
    }

    private fun cleanText(raw: String): String = SubtitleTextCleaner.tidy(
        SubtitleTextCleaner.unescapeHtmlEntities(
            SubtitleTextCleaner.stripHtmlTags(
                VTT_TIMESTAMP_TAG.replace(raw, "")
            )
        )
    )
}
