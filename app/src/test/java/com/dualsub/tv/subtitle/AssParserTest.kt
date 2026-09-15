package com.dualsub.tv.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssParserTest {

    @Test
    fun `keeps text containing commas intact`() {
        val content = """
            [Script Info]
            Title: 测试

            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,20

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:05.50,Default,,0,0,0,,你好，世界，这是带逗号的台词
        """.trimIndent()

        val cues = AssParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(5_500L, cues[0].endMs)
        assertEquals("你好，世界，这是带逗号的台词", cues[0].text)
    }

    @Test
    fun `expands ass line breaks and strips override tags`() {
        // 注意：下面是 raw string，反斜杠不做转义，
        // 所以这里的一个反斜杠就对应 ASS 文件里的一个反斜杠。
        val content = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:02.00,0:00:04.00,Default,,0,0,0,,{\an8}上方字幕\N第二行
        """.trimIndent()

        val cues = AssParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("上方字幕\n第二行", cues[0].text)
    }

    @Test
    fun `converts hard space escape to non breaking space`() {
        val content = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,前\h后
        """.trimIndent()

        val cues = AssParser.parse(content)

        assertEquals(1, cues.size)
        // \h 是 ASS 的不换行空格，不应像普通空白那样被清掉
        assertEquals("前\u00A0后", cues[0].text)
    }

    @Test
    fun `handles ssa style event section with fewer fields`() {
        val content = """
            [Events]
            Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: Marked=0,0:00:01.00,0:00:02.00,Default,NTP,0000,0000,0000,,SSA 格式
        """.trimIndent()

        val cues = AssParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("SSA 格式", cues[0].text)
    }

    @Test
    fun `ignores dialogue lines outside the events section`() {
        val content = """
            [Script Info]
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,不应被解析

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:03.00,0:00:04.00,Default,,0,0,0,,应被解析
        """.trimIndent()

        val cues = AssParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("应被解析", cues[0].text)
    }

    @Test
    fun `centisecond timestamps are converted to milliseconds`() {
        val content = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.05,0:00:01.50,Default,,0,0,0,,厘秒
        """.trimIndent()

        val cues = AssParser.parse(content)

        assertEquals(1, cues.size)
        // 1 秒 05 厘秒 = 1050ms；1 秒 50 厘秒 = 1500ms
        assertEquals(1_050L, cues[0].startMs)
        assertEquals(1_500L, cues[0].endMs)
    }

    @Test
    fun `returns empty for file without events`() {
        assertTrue(AssParser.parse("[Script Info]\nTitle: 空\n").isEmpty())
    }
}
