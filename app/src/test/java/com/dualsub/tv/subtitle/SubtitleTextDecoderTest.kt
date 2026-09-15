package com.dualsub.tv.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleTextDecoderTest {

    @Test
    fun `decodes plain utf8`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\n你好\n"
        assertEquals(text, SubtitleTextDecoder.decode(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `decodes utf8 with BOM`() {
        val text = "你好，世界"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + text.toByteArray(Charsets.UTF_8)

        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }

    @Test
    fun `falls back to gb18030 for non utf8 chinese subtitles`() {
        // 中文圈外挂字幕常见 GBK/GB18030 且不带 BOM
        val text = "你好，世界"
        val gb18030 = Charset.forName("GB18030")
        val bytes = text.toByteArray(gb18030)

        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }

    @Test
    fun `gb18030 subtitle file parses end to end`() {
        val content = "1\n00:00:01,000 --> 00:00:03,000\n中文字幕正常\n"
        val bytes = content.toByteArray(Charset.forName("GB18030"))

        val cues = SubtitleParsers.parseByFileName(bytes, "movie.zh.srt")

        assertEquals(1, cues.size)
        assertEquals("中文字幕正常", cues[0].text)
    }

    @Test
    fun `auto detects format when extension is unknown`() {
        val content = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n靠内容识别\n"

        val cues = SubtitleParsers.parseByFileName(content.toByteArray(Charsets.UTF_8), "subtitle.dat")

        assertEquals(1, cues.size)
        assertEquals("靠内容识别", cues[0].text)
    }

    @Test
    fun `unknown content yields no cues`() {
        val cues = SubtitleParsers.parseByFileName(
            "完全不是字幕的二进制垃圾".toByteArray(Charsets.UTF_8),
            null
        )

        assertTrue(cues.isEmpty())
    }
}
