package com.dualsub.tv.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ──────────────── ASS override 特效解析

    private fun singleDialogue(text: String) = """
        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,$text
    """.trimIndent()

    @Test
    fun `parses \an alignment tag`() {
        val cues = AssParser.parse(singleDialogue("""\{\an8}顶部居中"""))
        assertEquals(1, cues.size)
        assertEquals(8, cues[0].assOverride?.alignment)
        assertEquals("顶部居中", cues[0].text)
    }

    @Test
    fun `parses \pos absolute position with PlayResX and PlayResY`() {
        val content = """
            [Script Info]
            PlayResX: 1920
            PlayResY: 1080

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,{\pos(960,540)}中心
        """.trimIndent()
        val cues = AssParser.parse(content)
        assertEquals(1, cues.size)
        val ov = cues[0].assOverride!!
        assertEquals(0.5f, ov.posX!!, 0.001f)
        assertEquals(0.5f, ov.posY!!, 0.001f)
    }

    @Test
    fun `parses \fad fade in and out durations`() {
        val cues = AssParser.parse(singleDialogue("""\{\fad(200,300)}淡入淡出"""))
        assertEquals(1, cues.size)
        val ov = cues[0].assOverride!!
        assertEquals(200, ov.fadeInMs)
        assertEquals(300, ov.fadeOutMs)
    }

    @Test
    fun `parses \move animation coordinates`() {
        val content = """
            [Script Info]
            PlayResX: 640
            PlayResY: 480

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,{\move(0,240,640,240)}移动
        """.trimIndent()
        val cues = AssParser.parse(content)
        val ov = cues[0].assOverride!!
        assertEquals(0f, ov.moveFromX!!, 0.001f)
        assertEquals(0.5f, ov.moveFromY!!, 0.001f)
        assertEquals(1f, ov.moveToX!!, 0.001f)
        assertEquals(0.5f, ov.moveToY!!, 0.001f)
    }

    @Test
    fun `parses inline color tag \c`() {
        val cues = AssParser.parse(singleDialogue("""\{\c&H0000FF&}红色文字"""))
        assertEquals(1, cues.size)
        val span = cues[0].assOverride!!.spans.first()
        // ASS \c&H0000FF& = BGR: 00/00/FF → RGB: FF/00/00 = 不透明红
        assertEquals(0xFF_FF0000.toInt(), span.color)
    }

    @Test
    fun `parses \b \i \u style tags`() {
        val cues = AssParser.parse(singleDialogue("""\{\b1\i1\u1}样式"""))
        assertEquals(1, cues.size)
        val span = cues[0].assOverride!!.spans.first()
        assertEquals(true, span.bold)
        assertEquals(true, span.italic)
        assertEquals(true, span.underline)
    }

    @Test
    fun `parses karaoke \k segments`() {
        val cues = AssParser.parse(singleDialogue("""\{\k50}你{\k50}好"""))
        assertEquals(1, cues.size)
        val segs = cues[0].assOverride!!.karaokeSegments
        assertEquals(2, segs.size)
        assertEquals("你", segs[0].text)
        assertEquals(500, segs[0].durationMs)  // 50 centiseconds = 500ms
        assertEquals("好", segs[1].text)
    }

    @Test
    fun `parses \r reset clears style span`() {
        val cues = AssParser.parse(singleDialogue("""\{\c&H0000FF&}红\r白"""))
        assertEquals(1, cues.size)
        val spans = cues[0].assOverride!!.spans
        // 只有"红"这段有颜色，\r 之后恢复默认
        assertTrue(spans.any { it.endChar <= 1 && it.color != null })
        // 确认 text 是纯文字
        assertEquals("红白", cues[0].text)
    }

    @Test
    fun `parses \t color transform`() {
        val cues = AssParser.parse(singleDialogue("""\{\t(0,1000,\c&H00FF00&)}渐变"""))
        assertEquals(1, cues.size)
        val t = cues[0].assOverride!!.transforms.first()
        assertEquals(0L, t.t1Ms)
        assertEquals(1000L, t.t2Ms)
        assertNotNull(t.targetColor)
    }

    @Test
    fun `assOverride is null for plain text without override blocks`() {
        val cues = AssParser.parse(singleDialogue("普通字幕，没有标签"))
        assertEquals(1, cues.size)
        assertNull(cues[0].assOverride)
    }

    @Test
    fun `color conversion &H0000FFFF& is yellow in ARGB`() {
        // ASS &H00FFFF00& 是 BGR=00FF FF，即 B=0x00 G=0xFF R=0xFF → ARGB=FF FF FF 00 = 黄色
        // 用内部函数验证
        val color = AssOverrideParser.parseAssColor("0000FFFF")
        // BB=00 GG=FF RR=FF → ARGB = 0xFF_FFFF00
        assertEquals(0xFF_FFFF00.toInt(), color)
    }
}
