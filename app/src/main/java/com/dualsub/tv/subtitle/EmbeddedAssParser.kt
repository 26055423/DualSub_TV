package com.dualsub.tv.subtitle

/** Adapts Media3's Matroska SSA samples to the existing ASS effect parser. */
class EmbeddedAssParser(initializationData: List<ByteArray>) {
    // Matroska's event Format includes ReadOrder. CodecPrivate's original
    // event Format has a different column order and must not override it.
    private val eventFormat = initializationData.firstOrNull()
        ?.let(SubtitleTextDecoder::decode)
        ?.trim()?.takeIf { it.startsWith("Format:", ignoreCase = true) }
        ?: throw IllegalArgumentException("内嵌 ASS 缺少事件字段定义")
    private val header = initializationData.drop(1)
        .joinToString("\n") { SubtitleTextDecoder.decode(it) }
    private val prefix = "$header\n[Events]\n$eventFormat\n"

    fun parse(bytes: ByteArray, sampleTimeUs: Long): List<SubtitleCue> {
        // Media3 prepends relative start/end times to ReadOrder,Layer,...,Text.
        val raw = SubtitleTextDecoder.decode(bytes).trimEnd('\u0000', '\r', '\n')
        // MatroskaExtractor 1.6 writes H:MM:SS:cc (colon before centiseconds).
        // Normalize only the two leading timestamps, never the dialogue text.
        val dialogue = raw.split(',', limit = 3).let { parts ->
            if (parts.size != 3) raw else {
                parts.take(2).joinToString(",") { field ->
                    field.replace(Regex("""(\d+:\d+:\d+):(\d+)"""), "$1.$2")
                } + "," + parts[2]
            }
        }
        val offsetMs = sampleTimeUs / 1000
        return AssParser.parse(prefix + dialogue).map { cue ->
            cue.copy(startMs = offsetMs + cue.startMs, endMs = offsetMs + cue.endMs)
        }
    }
}
