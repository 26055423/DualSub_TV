package com.dualsub.tv.subtitle

/** 解析器注册表：按格式取解析器，或按内容自动判别。 */
object SubtitleParsers {

    private val ALL: List<SubtitleParser> = listOf(SrtParser, VttParser, AssParser)
    private val BY_FORMAT: Map<SubtitleFormat, SubtitleParser> = ALL.associateBy { it.format }

    fun of(format: SubtitleFormat): SubtitleParser? = BY_FORMAT[format]

    fun parse(content: String, format: SubtitleFormat): List<SubtitleCue> =
        BY_FORMAT[format]?.parse(content) ?: emptyList()

    fun parse(bytes: ByteArray, format: SubtitleFormat): List<SubtitleCue> =
        parse(SubtitleTextDecoder.decode(bytes), format)

    /**
     * 按文件名判定格式并解析；判定不出来时逐个解析器试跑，取第一个有结果的。
     * 用于外挂字幕文件与内嵌字幕轨两种来源。
     */
    fun parseByFileName(bytes: ByteArray, fileName: String?): List<SubtitleCue> {
        val text = SubtitleTextDecoder.decode(bytes)
        val byName = SubtitleFormat.fromFileName(fileName)
        if (byName != SubtitleFormat.UNKNOWN) {
            BY_FORMAT[byName]?.let { parser ->
                val cues = parser.parse(text)
                if (cues.isNotEmpty()) return cues
            }
        }
        return ALL.firstNotNullOfOrNull { parser ->
            parser.parse(text).takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }
}
