package com.dualsub.tv.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 字幕文本编码探测。
 *
 * 中文圈的外挂字幕经常是 GBK/GB18030 且不带 BOM，直接按 UTF-8 读会整篇乱码，
 * 所以这里按 BOM → 严格 UTF-8 → GB18030 → Latin-1 的顺序退让。
 */
object SubtitleTextDecoder {

    private val GB18030: Charset? = runCatching { Charset.forName("GB18030") }.getOrNull()

    fun decode(bytes: ByteArray): String {
        detectBom(bytes)?.let { (charset, offset) ->
            return String(bytes, offset, bytes.size - offset, charset)
        }
        strictUtf8(bytes)?.let { return it }
        // 通过局部捕获拿到非空类型，避免在 lambda 里对可空字段做智能转换
        GB18030?.let { gb18030 ->
            runCatching { String(bytes, gb18030) }.getOrNull()?.let { return it }
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun detectBom(bytes: ByteArray): Pair<Charset, Int>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8 to 3
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE to 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE to 2
        }
        return null
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
