package com.dualsub.tv.subtitle

/**
 * ASS / SSA 解析器。
 *
 * 在保留原有容错逻辑（字段顺序、逗号分割限制）的基础上，
 * 增加对 [Events] 之前 [Script Info] 段的 PlayResX/Y 读取，
 * 以及 override 块（`{...}`）的完整解析。
 *
 * 解析结果中 [SubtitleCue.assOverride] 为 null 当且仅当整条字幕
 * 没有任何有效的 override 标签。
 */
object AssParser : SubtitleParser {

    override val format = SubtitleFormat.ASS

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
        var inScriptInfo = false
        var fields = DEFAULT_FIELDS
        var playResX = 640f
        var playResY = 480f

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[")) {
                inScriptInfo = line.equals("[Script Info]", ignoreCase = true)
                inEventsSection = line.equals("[Events]", ignoreCase = true)
                continue
            }

            if (inScriptInfo) {
                when {
                    line.startsWith("PlayResX:", ignoreCase = true) ->
                        line.substringAfter(':').trim().toFloatOrNull()?.let { playResX = it }
                    line.startsWith("PlayResY:", ignoreCase = true) ->
                        line.substringAfter(':').trim().toFloatOrNull()?.let { playResY = it }
                }
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
                    parseDialogue(
                        line.substringAfter(':').trim(),
                        fields,
                        playResX,
                        playResY
                    )?.let { cues += it }
                }
            }
        }
        return cues.sortedAndDistinct()
    }

    private fun parseDialogue(
        body: String,
        fields: List<String>,
        playResX: Float,
        playResY: Float
    ): SubtitleCue? {
        val textIndex  = fields.indexOf("text").takeIf  { it >= 0 } ?: return null
        val startIndex = fields.indexOf("start").takeIf { it >= 0 } ?: return null
        val endIndex   = fields.indexOf("end").takeIf   { it >= 0 } ?: return null

        val parts = body.split(',', limit = fields.size)
        if (parts.size <= textIndex || parts.size <= startIndex || parts.size <= endIndex) return null

        val startMs = SubtitleTime.parse(parts[startIndex]) ?: return null
        val endMs   = SubtitleTime.parse(parts[endIndex])   ?: return null

        val rawText  = parts[textIndex]
        val override = AssOverrideParser.parse(rawText, playResX, playResY)
        val text     = cleanText(rawText)
        if (text.isEmpty()) return null

        return SubtitleCue(startMs, endMs, text, override)
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

/**
 * 从 ASS 字幕行的 Text 字段中解析 override 标签（`{...}` 块）。
 *
 * 解析策略：
 * 1. 用正则拆出所有 `{...}` 块与纯文字段；
 * 2. 维护一个"当前活跃样式"状态机，遇 `\r` 重置；
 * 3. 为每段纯文字记录字符偏移，生成 [AssSpan] 列表；
 * 4. 全局标签（`\pos`、`\an`、`\fad`、`\move` 等）取第一次出现的值。
 */
internal object AssOverrideParser {

    // 匹配整个 override 块，或块之间的纯文字
    // 同上：末尾 `}` 必须转义，否则 ICU 直接报 PatternSyntaxException（JVM 却接受）
    private val SEGMENT = Regex("""\{[^}]*\}|[^{]+""")
    // 单个标签：反斜杠 + 标签名 + 可选参数（括号内容 或 非反斜杠字符序列）
    private val TAG = Regex("""\\([a-zA-Z]+)(\([^)]*\)|[^\\{}\r\n]*)""")

    // 颜色格式：&HAABBGGRR& 或 &HBBGGRR&（AA 可选）
    private val COLOR_LONG  = Regex("""^&H([0-9A-Fa-f]{8})&?$""")
    private val COLOR_SHORT = Regex("""^&H([0-9A-Fa-f]{6})&?$""")

    fun parse(rawText: String, playResX: Float, playResY: Float): AssOverride? {
        var alignment: Int? = null
        var posX: Float? = null
        var posY: Float? = null
        var moveFromX: Float? = null
        var moveFromY: Float? = null
        var moveToX: Float? = null
        var moveToY: Float? = null
        var moveT1Ms = 0L
        var moveT2Ms = 0L
        var fadeInMs  = 0
        var fadeOutMs = 0
        val fadeParts = mutableListOf<FadePart>()
        val transforms = mutableListOf<AssTransform>()
        val spans = mutableListOf<AssSpan>()
        val karaokeSegments = mutableListOf<KaraokeSegment>()

        // 当前活跃的内联样式（在一个 override 块后延续，直到 \r 重置）
        var curColor: Int? = null
        var curBold: Boolean? = null
        var curItalic: Boolean? = null
        var curUnderline: Boolean? = null
        var curStrikeout: Boolean? = null
        var curFontSize: Float? = null

        // 纯文字字符偏移（累计剥离 override 块后的字符数）
        var charOffset = 0
        // 当前 span 的样式开始点
        var spanStart = 0
        var styleChangedSinceLastText = false

        fun flushSpan(endChar: Int) {
            if (endChar > spanStart && (curColor != null || curBold != null ||
                        curItalic != null || curUnderline != null ||
                        curStrikeout != null || curFontSize != null)) {
                spans += AssSpan(
                    startChar = spanStart,
                    endChar = endChar,
                    color = curColor,
                    bold = curBold,
                    italic = curItalic,
                    underline = curUnderline,
                    strikeout = curStrikeout,
                    fontSize = curFontSize
                )
            }
        }

        for (match in SEGMENT.findAll(rawText)) {
            val seg = match.value
            if (seg.startsWith('{')) {
                // override 块：如果之前有文字且样式将变化，先封闭当前 span
                if (styleChangedSinceLastText && charOffset > spanStart) {
                    flushSpan(charOffset)
                    spanStart = charOffset
                }

                for (tag in TAG.findAll(seg)) {
                    val name = tag.groupValues[1].lowercase()
                    val arg  = tag.groupValues[2].trim().trimEnd('&')

                    when (name) {
                        "an" -> if (alignment == null) alignment = arg.toIntOrNull()?.coerceIn(1, 9)
                        "a"  -> if (alignment == null) {
                            // SSA 旧版对齐：1=BL,2=BC,3=BR,5=TL,6=TC,7=TR,9=ML,10=MC,11=MR
                            val ssaA = arg.toIntOrNull()
                            alignment = ssaToAn(ssaA)
                        }
                        "pos" -> if (posX == null) {
                            val nums = parseFloatList(arg)
                            if (nums.size >= 2) {
                                posX = nums[0] / playResX
                                posY = nums[1] / playResY
                            }
                        }
                        "move" -> if (moveFromX == null) {
                            val nums = parseFloatList(arg)
                            if (nums.size >= 4) {
                                moveFromX = nums[0] / playResX
                                moveFromY = nums[1] / playResY
                                moveToX   = nums[2] / playResX
                                moveToY   = nums[3] / playResY
                                if (nums.size >= 6) {
                                    moveT1Ms = nums[4].toLong()
                                    moveT2Ms = nums[5].toLong()
                                }
                            }
                        }
                        "fad" -> if (fadeInMs == 0 && fadeOutMs == 0 && fadeParts.isEmpty()) {
                            val nums = parseFloatList(arg)
                            if (nums.size >= 2) {
                                fadeInMs  = nums[0].toInt()
                                fadeOutMs = nums[1].toInt()
                            }
                        }
                        "fade" -> if (fadeParts.isEmpty()) {
                            val nums = parseFloatList(arg)
                            if (nums.size >= 7) {
                                val a1 = 1f - nums[0] / 255f
                                val a2 = 1f - nums[1] / 255f
                                val a3 = 1f - nums[2] / 255f
                                val t1 = nums[3].toLong()
                                val t2 = nums[4].toLong()
                                val t3 = nums[5].toLong()
                                val t4 = nums[6].toLong()
                                fadeParts += FadePart(a1, 0L, t1)
                                fadeParts += FadePart(a2, t1, t2)
                                fadeParts += FadePart(a2, t2, t3)
                                fadeParts += FadePart(a3, t3, t4)
                            }
                        }
                        "t" -> {
                            // \t([t1,t2,][accel,]<tags>)
                            val transform = parseTransform(arg)
                            if (transform != null) transforms += transform
                        }
                        // 主颜色：\c 或 \1c（ASS 格式 &HAABBGGRR&）
                        "c", "1c" -> {
                            val color = parseAssColor(arg)
                            if (color != curColor) {
                                if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                                curColor = color
                                styleChangedSinceLastText = true
                            }
                        }
                        "b" -> {
                            val v = arg.toIntOrNull()?.let { it != 0 } ?: (arg == "1")
                            if (v != curBold) {
                                if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                                curBold = if (v) true else null
                                styleChangedSinceLastText = true
                            }
                        }
                        "i" -> {
                            val v = arg.toIntOrNull()?.let { it != 0 } ?: (arg == "1")
                            if (v != curItalic) {
                                if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                                curItalic = if (v) true else null
                                styleChangedSinceLastText = true
                            }
                        }
                        "u" -> {
                            val v = arg.toIntOrNull()?.let { it != 0 } ?: (arg == "1")
                            if (v != curUnderline) {
                                if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                                curUnderline = if (v) true else null
                                styleChangedSinceLastText = true
                            }
                        }
                        "s" -> {
                            val v = arg.toIntOrNull()?.let { it != 0 } ?: (arg == "1")
                            if (v != curStrikeout) {
                                if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                                curStrikeout = if (v) true else null
                                styleChangedSinceLastText = true
                            }
                        }
                        "fs" -> {
                            val sz = arg.toFloatOrNull()
                            if (sz != curFontSize) {
                                if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                                curFontSize = sz
                                styleChangedSinceLastText = true
                            }
                        }
                        "r" -> {
                            // 重置：封闭当前 span，清空所有内联样式
                            if (charOffset > spanStart) { flushSpan(charOffset); spanStart = charOffset }
                            curColor = null; curBold = null; curItalic = null
                            curUnderline = null; curStrikeout = null; curFontSize = null
                            styleChangedSinceLastText = true
                        }
                        // 卡拉OK
                        "k", "K" -> {
                            val dur = arg.toIntOrNull() ?: 0
                            // 文字段在下一个纯文字 segment 里，先记录时长（文字待填充）
                            karaokeSegments += KaraokeSegment("", dur * 10, KaraokeType.K)
                        }
                        "kf" -> {
                            val dur = arg.toIntOrNull() ?: 0
                            karaokeSegments += KaraokeSegment("", dur * 10, KaraokeType.KF)
                        }
                        "ko" -> {
                            val dur = arg.toIntOrNull() ?: 0
                            karaokeSegments += KaraokeSegment("", dur * 10, KaraokeType.KO)
                        }
                    }
                }
            } else {
                // 纯文字段
                // 展开 ASS 换行 / 硬空格（与 cleanText 一致，但这里不剥标签）
                val expanded = seg
                    .replace(Regex("""\\[Nn]"""), "\n")
                    .replace(Regex("""\\h"""), "\u00A0")

                // 卡拉OK：把最后一个没有文字的段填充文字
                if (karaokeSegments.isNotEmpty()) {
                    val last = karaokeSegments.last()
                    if (last.text.isEmpty()) {
                        karaokeSegments[karaokeSegments.lastIndex] = last.copy(text = expanded)
                    }
                }

                charOffset += expanded.length
                styleChangedSinceLastText = false
            }
        }

        // 最后一段文字的样式还未封闭
        flushSpan(charOffset)

        val hasAny = alignment != null || posX != null || moveFromX != null ||
                fadeInMs != 0 || fadeOutMs != 0 || fadeParts.isNotEmpty() ||
                transforms.isNotEmpty() || spans.isNotEmpty() ||
                karaokeSegments.any { it.text.isNotEmpty() }

        if (!hasAny) return null

        return AssOverride(
            alignment = alignment,
            posX = posX, posY = posY,
            moveFromX = moveFromX, moveFromY = moveFromY,
            moveToX = moveToX, moveToY = moveToY,
            moveT1Ms = moveT1Ms, moveT2Ms = moveT2Ms,
            fadeInMs = fadeInMs, fadeOutMs = fadeOutMs,
            fadeParts = fadeParts,
            transforms = transforms,
            spans = spans,
            karaokeSegments = karaokeSegments.filter { it.text.isNotEmpty() }
        )
    }

    /** SSA 旧版 \a 对齐值转换为 \an 值（numpad 布局）。 */
    private fun ssaToAn(a: Int?): Int? = when (a) {
        1 -> 1; 2 -> 2; 3 -> 3    // 底部
        5 -> 7; 6 -> 8; 7 -> 9    // 顶部
        9 -> 4; 10 -> 5; 11 -> 6  // 中部
        else -> null
    }

    /** 解析括号内的逗号分隔浮点数列表（括号本身已在调用前去掉）。 */
    private fun parseFloatList(arg: String): List<Float> {
        val inner = arg.removePrefix("(").removeSuffix(")")
        return inner.split(',').mapNotNull { it.trim().toFloatOrNull() }
    }

    /**
     * 解析 `\t(...)` 参数字符串，格式：
     * `[t1,t2,][accel,]<tags>`  （所有括号已被剥掉）
     */
    private fun parseTransform(arg: String): AssTransform? {
        val inner = arg.removePrefix("(").removeSuffix(")")
        val parts = inner.split(',')
        var t1 = 0L; var t2 = 0L; var accel = 1f
        var tagStart = 0

        // 数字前缀：最多 3 个（t1,t2,accel 或 t1,t2 或 accel）
        val numPrefix = parts.takeWhile { it.trim().toFloatOrNull() != null }
        when (numPrefix.size) {
            3 -> { t1 = numPrefix[0].trim().toLong(); t2 = numPrefix[1].trim().toLong()
                   accel = numPrefix[2].trim().toFloat(); tagStart = 3 }
            2 -> { t1 = numPrefix[0].trim().toLong(); t2 = numPrefix[1].trim().toLong(); tagStart = 2 }
            1 -> { accel = numPrefix[0].trim().toFloat(); tagStart = 1 }
        }

        val tagStr = parts.drop(tagStart).joinToString(",")
        var targetColor: Int? = null
        var targetFontSize: Float? = null
        var targetBold: Boolean? = null
        var targetItalic: Boolean? = null

        for (tag in TAG.findAll("{$tagStr}")) {
            val name = tag.groupValues[1].lowercase()
            val v    = tag.groupValues[2].trim().trimEnd('&')
            when (name) {
                "c", "1c" -> targetColor    = parseAssColor(v)
                "fs"       -> targetFontSize = v.toFloatOrNull()
                "b"        -> targetBold     = v.toIntOrNull()?.let { it != 0 }
                "i"        -> targetItalic   = v.toIntOrNull()?.let { it != 0 }
            }
        }

        if (targetColor == null && targetFontSize == null &&
            targetBold == null && targetItalic == null) return null

        return AssTransform(t1Ms = t1, t2Ms = t2, accel = accel,
            targetColor = targetColor, targetFontSize = targetFontSize,
            targetBold = targetBold, targetItalic = targetItalic)
    }

    /**
     * 将 ASS 颜色字符串转换为 Android ARGB Int（0xAARRGGBB）。
     *
     * ASS 格式为 `&HAABBGGRR&`（AA 可选；BB/GG/RR 各 2 位十六进制），
     * 其中 AA=0x00 表示完全不透明（与 Android 相反），需做 alpha 反转。
     */
    internal fun parseAssColor(raw: String): Int? {
        val s = raw.removePrefix("&H").removeSuffix("&").uppercase().trim()
        return when {
            COLOR_LONG.matches("&H$s&") || s.length == 8 -> {
                val aa = s.substring(0, 2).toIntOrNull(16) ?: return null
                val bb = s.substring(2, 4).toIntOrNull(16) ?: return null
                val gg = s.substring(4, 6).toIntOrNull(16) ?: return null
                val rr = s.substring(6, 8).toIntOrNull(16) ?: return null
                val androidAlpha = 255 - aa
                (androidAlpha shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
            s.length == 6 -> {
                val bb = s.substring(0, 2).toIntOrNull(16) ?: return null
                val gg = s.substring(2, 4).toIntOrNull(16) ?: return null
                val rr = s.substring(4, 6).toIntOrNull(16) ?: return null
                (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
            else -> null
        }
    }
}
