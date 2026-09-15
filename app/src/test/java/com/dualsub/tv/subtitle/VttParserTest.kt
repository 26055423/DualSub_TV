package com.dualsub.tv.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VttParserTest {

    private fun vtt(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

    @Test
    fun `parses header cue id and settings`() {
        val content = vtt(
            "WEBVTT",
            "",
            "cue-1",
            "00:00:01.000 --> 00:00:04.000 line:90% align:middle",
            "第一句",
            "",
            "00:00:05.500 --> 00:00:08.000",
            "第二句"
        )

        val cues = VttParser.parse(content)

        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(4_000L, cues[0].endMs)
        assertEquals("第一句", cues[0].text)
        assertEquals(5_500L, cues[1].startMs)
    }

    @Test
    fun `accepts timestamps without hours`() {
        val content = vtt(
            "WEBVTT",
            "",
            "01:30.500 --> 01:35.000",
            "省略小时段"
        )

        val cues = VttParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals(90_500L, cues[0].startMs)
        assertEquals(95_000L, cues[0].endMs)
    }

    @Test
    fun `skips NOTE blocks`() {
        val content = vtt(
            "WEBVTT",
            "",
            "NOTE 这是一段注释",
            "里面甚至可能有 --> 这样的字符",
            "",
            "00:00:01.000 --> 00:00:02.000",
            "正文"
        )

        val cues = VttParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("正文", cues[0].text)
    }

    @Test
    fun `strips voice and class markup`() {
        val content = vtt(
            "WEBVTT",
            "",
            "00:00:01.000 --> 00:00:02.000",
            "<v Speaker><c.yellow>这是台词</c>"
        )

        val cues = VttParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("这是台词", cues[0].text)
    }

    @Test
    fun `drops inline timestamp tags`() {
        val content = vtt(
            "WEBVTT",
            "",
            "00:00:01.000 --> 00:00:02.000",
            "前面<00:00:01.500>后面"
        )

        val cues = VttParser.parse(content)

        assertEquals(1, cues.size)
        assertEquals("前面后面", cues[0].text)
    }

    @Test
    fun `returns empty for empty file`() {
        assertTrue(VttParser.parse("WEBVTT\n").isEmpty())
    }
}
