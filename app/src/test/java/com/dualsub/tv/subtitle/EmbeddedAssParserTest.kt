package com.dualsub.tv.subtitle

import org.junit.Assert.*
import org.junit.Test

class EmbeddedAssParserTest {
    private val format = "Format: Start, End, ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
    private val header = """
        [Script Info]
        PlayResX: 1920
        PlayResY: 1080
        [V4+ Styles]
        Format: Name, Fontname, Fontsize
        Style: Default,Arial,48
        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
    """.trimIndent()

    @Test fun `preserves embedded effects resolution commas and container timing`() {
        val parser = EmbeddedAssParser(listOf(format.toByteArray(), header.toByteArray()))
        val cue = parser.parse(
            """Dialogue: 0:00:00:00,0:00:02:50,7,0,Default,,0,0,0,,{\an8\pos(960,270)\fad(200,300)\b1}Hello,世界\N第二行""".toByteArray(),
            6_757_000L
        ).single()
        assertEquals(6757L, cue.startMs)
        assertEquals(9257L, cue.endMs)
        assertEquals("Hello,世界\n第二行", cue.text)
        val effect = requireNotNull(cue.assOverride)
        assertEquals(8, effect.alignment)
        assertEquals(0.5f, effect.posX!!, 0.001f)
        assertEquals(0.25f, effect.posY!!, 0.001f)
        assertEquals(200, effect.fadeInMs)
        assertEquals(300, effect.fadeOutMs)
        assertEquals(true, effect.spans.first().bold)
    }

    @Test fun `keeps move transform and karaoke instead of flattening to text`() {
        val parser = EmbeddedAssParser(listOf(format.toByteArray(), header.toByteArray()))
        val cue = parser.parse(
            """Dialogue: 0:00:00:00,0:00:04:00,0,0,Default,,0,0,0,,{\move(0,0,1920,1080,0,4000)\t(0,1000,\fs60)\k50}你{\kf75}好""".toByteArray(),
            0L
        ).single()
        val effect = requireNotNull(cue.assOverride)
        assertEquals(0L, cue.startMs)
        assertEquals(4000L, cue.endMs)
        assertEquals(1f, effect.moveToX!!, 0f)
        assertEquals(4000L, effect.moveT2Ms)
        assertEquals(60f, effect.transforms.single().targetFontSize!!, 0f)
        assertEquals(listOf(500, 750), effect.karaokeSegments.map { it.durationMs })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing initialization fails explicitly`() {
        EmbeddedAssParser(emptyList())
    }
}
