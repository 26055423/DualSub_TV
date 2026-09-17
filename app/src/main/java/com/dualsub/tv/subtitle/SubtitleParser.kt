package com.dualsub.tv.subtitle

/** 各路字幕解析器的统一入口。实现必须是无状态、可复用的。 */
interface SubtitleParser {

    val format: SubtitleFormat

    /**
     * 解析已解码为字符串的字幕正文。
     * 无法识别的条目会被跳过而不是抛异常，返回结果按开始时间升序排列。
     */
    fun parse(content: String): List<SubtitleCue>
}

/** 解析前后共用的文本清理。 */
internal object SubtitleTextCleaner {

    private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
    // **末尾的 `}` 必须转义**：ICU 正则引擎（Android 用的就是它）要求 `{`/`}` 严格配对，
    // 看到孤立的 `}` 会直接抛 PatternSyntaxException；而 JVM 的 java.util.regex 宽容地接受。
    // 后果是纯 JVM 单测全绿、真机上这个 object 连类都初始化不了，
    // 表现成 SrtParser 抛 ExceptionInInitializerError、一条字幕都解不出。
    private val ASS_OVERRIDE = Regex("""\{[^}]*\}""")

    /** 去掉 SRT/VTT 的内联标签，如 `<i>`、`<font ...>`。 */
    fun stripHtmlTags(text: String): String = HTML_TAG.replace(text, "")

    /** 去掉 ASS 的覆盖指令，如 `{\pos(10,20)}`、`{\an8}`。 */
    fun stripAssOverrides(text: String): String = ASS_OVERRIDE.replace(text, "")

    fun unescapeHtmlEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    /** 统一换行符，便于后续按行处理。 */
    fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    /** 折叠多余空白行、去掉整体前后空白。 */
    fun tidy(text: String): String = text
        .split('\n')
        .joinToString("\n") { it.trim() }
        .trim('\n')
        .trim()
}

/** 按开始时间排序并在需要时去除完全重复的条目。 */
internal fun List<SubtitleCue>.sortedAndDistinct(): List<SubtitleCue> =
    sortedWith(compareBy({ it.startMs }, { it.endMs }))
        .distinctBy { Triple(it.startMs, it.endMs, it.text) }
