package com.dualsub.tv.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {

    private fun srt(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

    @Test
    fun `parses two basic cues`() {
        val content = srt(
            "1",
            "00:00:01,000 --> 00:00:05,000",
            "Hello",
            "",
            "2",
            "00:00:06,500 --> 00:00:10,250",
            "World",
            "Second line"
        )

        val cues = SrtParser.parse(content)

        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(5_000L, cues[0].endMs)
        assertEquals("Hello", cues[0].text)
        assertEquals(6_500L, cues[1].startMs)
        assertEquals(10_250L, cues[1].endMs)
        assertEquals("World\nSecond line", cues[1].text)
    }

    @Test
    fun `tolerates CRLF newlines`() {
        val content = "1\r\n00:00:02,000 --> 00:00:03,000\r\nCRLF 测试\r\n"

        val cues = SrtParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("CRLF 测试", cues[0].text)
    }

    @Test
    fun `tolerates missing index line`() {
        val content = srt(
            "00:00:01,000 --> 00:00:02,000",
            "无序号",
            "",
            "00:00:03,000 --> 00:00:04,000",
            "第二条"
        )

        val cues = SrtParser.parse(content)

        assertEquals(2, cues.size)
        assertEquals("无序号", cues[0].text)
    }

    @Test
    fun `strips inline html tags and unescapes entities`() {
        val content = srt(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "<i>斜体</i> &amp; <font color=\"#fff\">彩色</font> &lt;标记&gt;"
        )

        val cues = SrtParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("斜体 & 彩色 <标记>", cues[0].text)
    }

    @Test
    fun `ignores legacy coordinate suffix`() {
        val content = srt(
            "1",
            "00:00:01,000 --> 00:00:02,000  X1:40 X2:600 Y1:20 Y2:50",
            "带坐标"
        )

        val cues = SrtParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals(2_000L, cues[0].endMs)
        assertEquals("带坐标", cues[0].text)
    }

    @Test
    fun `returns sorted and deduplicated cues`() {
        val content = srt(
            "1",
            "00:00:09,000 --> 00:00:10,000",
            "后出现",
            "",
            "2",
            "00:00:01,000 --> 00:00:02,000",
            "先出现",
            "",
            "3",
            "00:00:01,000 --> 00:00:02,000",
            "先出现"
        )

        val cues = SrtParser.parse(content)

        assertEquals(2, cues.size)
        assertEquals("先出现", cues[0].text)
        assertEquals("后出现", cues[1].text)
    }

    @Test
    fun `skips blocks without a timestamp line`() {
        val cues = SrtParser.parse("这不是字幕文件\n随便写点东西\n")

        assertTrue(cues.isEmpty())
    }
}
